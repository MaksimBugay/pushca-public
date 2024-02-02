const Command = Object.freeze({
    PING: "PING",
    SEND_MESSAGE: "SEND_MESSAGE",
    SEND_MESSAGE_WITH_ACKNOWLEDGE: "SEND_MESSAGE_WITH_ACKNOWLEDGE"
});

const ResponseType = Object.freeze({
    SUCCESS: "SUCCESS",
    ERROR: "ERROR"
});

const MessageType = Object.freeze({
    ACKNOWLEDGE: "ACKNOWLEDGE",
    RESPONSE: "RESPONSE",
    CHANNEL_MESSAGE: "CHANNEL_MESSAGE",
    CHANNEL_EVENT: "CHANNEL_EVENT"
});

const MessagePartsDelimiter = "@@";

class WaterResponse {
    constructor(type, body) {
        this.type = type;
        this.body = body;
    }
}

class Water {
    constructor() {
        this.promise = new Promise((resolve, reject) => {
            this.resolve = resolve; // Assign resolve function to the outer scope variable
            this.reject = reject; // Assign reject function to the outer scope variable
        });
    }
}

function releaseWaterWithSuccess(water, response) {
    water.resolve(new WaterResponse(ResponseType.SUCCESS, response))
}

function releaseWaterWithError(water, error) {
    water.reject(new WaterResponse(ResponseType.ERROR, error));
}

class CommandWithId {
    constructor(id, message) {
        this.id = id;
        this.message = message;
    }
}

let PushcaClient = {};
PushcaClient.waitingHall = new Map();
PushcaClient.serverBaseUrl = 'http://localhost:8050'

PushcaClient.addToWaitingHall = function (id) {
    let water = new Water();
    PushcaClient.waitingHall.set(id, water);
    return water.promise;
}

PushcaClient.releaseWaterIfExists = function (id, response) {
    let water = PushcaClient.waitingHall.get(id);
    if (water) {
        releaseWaterWithSuccess(water, response)
        PushcaClient.waitingHall.delete(id);
    }
}
PushcaClient.executeWithRepeatOnFailure = async function (id, commandWithId, inTimeoutMs) {
    return PushcaClient.execute(id, commandWithId, inTimeoutMs);
}
PushcaClient.execute = async function (id, commandWithId, inTimeoutMs) {
    let timeoutMs = inTimeoutMs || 5000;
    let ackId = id || commandWithId.id;

    let timeout = (ms) => new Promise((resolve, reject) => {
        setTimeout(() => reject(new Error('Timeout after ' + ms + ' ms')), ms);
    });

    PushcaClient.ws.send(commandWithId.message);
    let result;
    try {
        result = await Promise.race([
            PushcaClient.addToWaitingHall(ackId),
            timeout(timeoutMs)
        ]);
    } catch (error) {
        PushcaClient.waitingHall.delete(ackId);
        result = new WaterResponse(ResponseType.ERROR, error)
    }
    console.log(result);
    return result;
}

PushcaClient.buildCommandMessage = function (command, args) {
    let id = crypto.randomUUID();
    let message = `${id}${MessagePartsDelimiter}${command}${MessagePartsDelimiter}${JSON.stringify(args)}`;
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
    PushcaClient.executeWithRepeatOnFailure(null, commandWithId)
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
                    let parts = event.data.split(MessagePartsDelimiter);
                    if (parts[1] === MessageType.RESPONSE) {
                        let body;
                        if (parts.length > 2) {
                            body = parts[2];
                        }
                        PushcaClient.releaseWaterIfExists(parts[0], body)
                        return
                    }
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