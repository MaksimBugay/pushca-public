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