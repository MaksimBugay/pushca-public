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
    $("#p-send-with-acknowledge").click(function () {
        let filterObj = {};
        filterObj["workSpaceId"] = "workSpaceMain";
        filterObj["accountId"] = "clientWeb1@test.ee";
        filterObj["deviceId"] = "Chrome";
        filterObj["applicationId"] = "MLA_JAVA_HEADLESS";
        PushcaClient.sendMessageWithAcknowledge(
            crypto.randomUUID(),
            filterObj,
            true,
            $('#p-message').val()
        );
    });

    let clientObj = {};
    clientObj["workSpaceId"] = "workSpaceMain";
    clientObj["accountId"] = "clientWeb1@test.ee";
    clientObj["deviceId"] = getBrowserName();
    //clientObj["deviceId"] = crypto.randomUUID();
    clientObj["applicationId"] = "MLA_JAVA_HEADLESS";

    $("#l-client").text(JSON.stringify(clientObj));

    PushcaClient.openConnection(clientObj,
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
        function (ws, messageText) {
            if (messageText !== "PONG") {
                let history = $("textarea#p-history");
                history.val(history.val() + messageText + "\n");
            }
        }
    );
});