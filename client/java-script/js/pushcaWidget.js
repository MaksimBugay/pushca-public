$(document).ready(function () {
    $('#p-message').val("test message" + Date.now());
    $("#p-send").click(function () {
        let filterObj = new ClientFilter(
            "workSpaceMain",
            null,
            null,
            "MLA_JAVA_HEADLESS"
        );
        PushcaClient.broadcastMessage(
            crypto.randomUUID(),
            filterObj,
            true,
            $('#p-message').val()
        );
    });
    $("#p-send-with-acknowledge").click(function () {
        let filterObj = new ClientFilter(
            "workSpaceMain",
            "clientWeb1@test.ee",
            "Chrome",
            "MLA_JAVA_HEADLESS"
        );
        PushcaClient.sendMessageWithAcknowledge(
            crypto.randomUUID(),
            filterObj,
            true,
            $('#p-message').val()
        );
    });
    $("#p-add-members-to-channel").click(function () {
        let channel = new PChannel("12345", "test-channel");
        let filterObj1 = new ClientFilter(
            "workSpaceMain",
            "clientWeb1@test.ee",
            null,
            "MLA_JAVA_HEADLESS"
        );
        let filterObj2 = new ClientFilter(
            "workSpaceMain",
            "clientWeb2@test.ee",
            null,
            "MLA_JAVA_HEADLESS"
        );
        PushcaClient.addMembersToChannel(channel, [filterObj1, filterObj2]);
    });

    let clientObj = new ClientFilter(
        "workSpaceMain",
        "clientWeb1@test.ee",
        getBrowserName(),
        "MLA_JAVA_HEADLESS"
    );

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
        },
        function (channelEvent) {
            let channelEvents = $("textarea#p-channel-events");
            channelEvents.val(channelEvents.val() + channelEvent.type + "\n");
            channelEvents.val(channelEvents.val() + channelEvent.channelId + "\n");
            channelEvents.val(channelEvents.val() + channelEvent.actor.deviceId + "\n");
            channelEvents.val(channelEvents.val() + channelEvent.filters.map(filter => filter.accountId).join(";") + "\n");
            channelEvents.val(channelEvents.val() + "------------------------" + "\n");
        }
    );
});