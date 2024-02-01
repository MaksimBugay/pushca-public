let PushcaClient = {};
PushcaClient.serverBaseUrl = 'http://localhost:8050'

PushcaClient.openConnection = function (initWsCallback) {
    let requestObj = {};
    let clientObj = {};
    clientObj["workSpaceId"] = "workSpaceMain";
    clientObj["accountId"] = "clientWeb1@test.ee";
    clientObj["deviceId"] = "D" + Date.now();
    clientObj["applicationId"] = "MLA_JAVA_HEADLESS";
    requestObj["client"] = clientObj;
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
            initWsCallback(PushcaClient.ws)
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
        let sendRequestObj = {};
        let filterObj = {};
        filterObj["workSpaceId"] = "workSpaceMain";
        filterObj["applicationId"] = "MLA_JAVA_HEADLESS";
        sendRequestObj["filter"] = filterObj;
        sendRequestObj["message"] = $('#p-message').val();
        $.ajax({
            contentType: 'application/json',
            data: JSON.stringify(sendRequestObj),
            dataType: 'json',
            success: function (data) {
                console.log("Attempt to send push notification succeeded");
            },
            error: function (xhr, textStatus, errorThrown) {
                console.log(
                    "Attempt to send push notification failed " + xhr.responseText);
                console.log(textStatus);
                console.log(errorThrown);
            },
            type: 'POST',
            url: PushcaClient.serverBaseUrl + '/send-notification'
        });
    });

    PushcaClient.openConnection(function (ws){
        if (ws) {
            let intervalId = window.setInterval(function () {
                PushcaClient.ws.send(JSON.stringify({"command": "PING"}));
            }, 20000);
            ws.onopen = function () {
                console.log('open');
            };

            ws.onmessage = function (e) {
                console.log('message', e.data);
                if (e.data !== "PONG") {
                    //alert(e.data);
                    let history = $("textarea#p-history");
                    history.val(history.val() + e.data + "\n");
                }
            };

            ws.onerror = function (error) {
                console.log("There was an error with your websocket!");
            };

            ws.onclose = function (event) {
                window.clearInterval(intervalId);
                if (event.wasClean) {
                    console.log(
                        `[close] Connection closed cleanly, code=${event.code} reason=${event.reason}`);
                } else {
                    // e.g. server process killed or network down
                    // event.code is usually 1006 in this case
                    //alert('[close] Connection died');
                    $("#l-message").text("Your connection died, refresh the page please");
                }
            };
        }
    });
});