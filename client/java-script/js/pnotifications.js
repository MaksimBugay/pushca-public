const Command = Object.freeze({
    PING: "PING",
    SEND_MESSAGE: "SEND_MESSAGE",
    SEND_MESSAGE_WITH_ACKNOWLEDGE: "SEND_MESSAGE_WITH_ACKNOWLEDGE"
});

const MESSAGE_PARTS_DELIMITER = "@@";

class CommandWithId {
    constructor(id, message) {
        this.id = id;
        this.message = message;
    }
}

let PushcaClient = {};
PushcaClient.waitingHall = new Map();
PushcaClient.serverBaseUrl = 'http://localhost:8050'

PushcaClient.buildCommandMessage = function (command, args) {
    let id = crypto.randomUUID();
    let message = `${id}${MESSAGE_PARTS_DELIMITER}${command}${MESSAGE_PARTS_DELIMITER}${JSON.stringify(args)}`;
    return new CommandWithId(id, message);
}

PushcaClient.broadcastMessage = function (id, dest, preserveOrder, message) {
    let metaData = {};
    metaData["id"] = id;
    metaData["filter"] = dest;
    metaData["sender"] = PushcaClient.client;
    metaData["message"] = message;
    metaData["preserveOrder"] = preserveOrder;

    let commandWithId = PushcaClient.buildCommandMessage(Command.SEND_MESSAGE, metaData);
    PushcaClient.ws.send(commandWithId.message);
}

PushcaClient.openConnection = function (onOpenHandler, onCloseHandler, onMessageHandler) {
    let requestObj = {};
    PushcaClient.ClientObj = {};
    PushcaClient.ClientObj["workSpaceId"] = "workSpaceMain";
    PushcaClient.ClientObj["accountId"] = "clientWeb1@test.ee";
    PushcaClient.ClientObj["deviceId"] = crypto.randomUUID();
    PushcaClient.ClientObj["applicationId"] = "MLA_JAVA_HEADLESS";
    requestObj["client"] = PushcaClient.ClientObj;
    $.ajax({
        contentType: 'application/json',
        data: JSON.stringify(requestObj),
        dataType: 'json',
        success: function (data) {
            let wsUrl
            $.each(data, function (index, element) {
                if (index === "browserAdvertisedUrl") {
                    wsUrl = element
                }
            });
            console.log("Ws connection url was acquired: " + wsUrl);

            PushcaClient.ws = new WebSocket(wsUrl);
            if (PushcaClient.ws) {
                PushcaClient.ws.onopen = function () {
                    console.log('open');
                    onOpenHandler(PushcaClient.ws);
                };

                PushcaClient.ws.onmessage = function (event) {
                    console.log('message', event.data);
                    onMessageHandler(PushcaClient.ws, event)
                };

                PushcaClient.ws.onerror = function (error) {
                    console.log("There was an error with your websocket!");
                };

                PushcaClient.ws.onclose = function (event) {
                    if (event.wasClean) {
                        console.log(
                            `[close] Connection closed cleanly, code=${event.code} reason=${event.reason}`);
                    }
                    onCloseHandler(PushcaClient.ws, event)
                };
            }
        },
        error: function () {
            console.log("Attempt to acquire ws connection url failed");
        },
        processData: false,
        type: 'POST',
        url: PushcaClient.serverBaseUrl + '/open-connection'
    });
};

$(document).ready(function () {
    $('#p-message').val("test message" + Date.now());
    $("#p-send").click(function () {
        let filterObj = {};
        filterObj["workSpaceId"] = "workSpaceMain";
        filterObj["applicationId"] = "MLA_JAVA_HEADLESS";
        PushcaClient.broadcastMessage(
            crypto.randomUUID(),
            filterObj,
            true,
            $('#p-message').val()
        );
    });

    PushcaClient.openConnection(
        function (ws) {
            PushcaClient.PingIntervalId = window.setInterval(function () {
                PushcaClient.ws.send(JSON.stringify({"command": "PING"}));
            }, 20000);
        },
        function (ws, event) {
            window.clearInterval(PushcaClient.PingIntervalId);
            if (!event.wasClean) {
                $("#l-message").text("Your connection died, refresh the page please");
            }
        },
        function (ws, event) {
            if (event.data !== "PONG") {
                let history = $("textarea#p-history");
                history.val(history.val() + event.data + "\n");
            }
        }
    );
});