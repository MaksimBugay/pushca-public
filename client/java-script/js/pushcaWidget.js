function filtersToAccountList(filters) {
    return filters.map(filter => filter.accountId).join(";");
}

function getQueryParam(paramName) {
    const query = window.location.search.substring(1);
    const vars = query.split('&');

    for (let i = 0; i < vars.length; i++) {
        const pair = vars[i].split('=');
        if (decodeURIComponent(pair[0]) === paramName) {
            return decodeURIComponent(pair[1]);
        }
    }
    return null;
}

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
            "clientWeb1",
            "D100",
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
        let channel = new PChannel("CH12345", "test-channel");
        let filterObj1 = new ClientFilter(
            "workSpaceMain",
            "clientWeb1",
            null,
            "MLA_JAVA_HEADLESS"
        );
        let filterObj2 = new ClientFilter(
            "workSpaceMain",
            "clientWeb2",
            null,
            "MLA_JAVA_HEADLESS"
        );
        let filterObj3 = new ClientFilter(
            "workSpaceMain",
            "clientWeb3",
            null,
            "MLA_JAVA_HEADLESS"
        );
        PushcaClient.addMembersToChannel(channel, [filterObj1, filterObj2, filterObj3]);
    });

    $("#p-send-message-to-channel").click(function () {
        let channel = new PChannel("CH12345", "test-channel");
        let filterObj2 = new ClientFilter(
            "workSpaceMain",
            "clientWeb2",
            null,
            "MLA_JAVA_HEADLESS"
        );
        let filterObj3 = new ClientFilter(
            "workSpaceMain",
            "clientWeb3",
            null,
            "MLA_JAVA_HEADLESS"
        );
        PushcaClient.sendMessageToChannel(channel, [filterObj2, filterObj3], $('#p-message').val());
    });

    let clientObj = new ClientFilter(
        "workSpaceMain",
        getQueryParam("account-id"),
        getQueryParam("device-id"),
        "MLA_JAVA_HEADLESS"
    );

    $("#l-client").text(printObject(clientObj));

    PushcaClient.openConnection('http://localhost:8050', clientObj,
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
            channelEvents.val(channelEvents.val() + "actor: " + printObject(channelEvent.actor) + "\n");
            channelEvents.val(channelEvents.val() + "members: " + filtersToAccountList(channelEvent.filters) + "\n");
            channelEvents.val(channelEvents.val() + "------------------------" + "\n");
        },
        function (channelMessage) {
            console.log(JSON.stringify(channelMessage));
            let channelMessages = $("textarea#p-channel-messages");
            channelMessages.val(channelMessages.val() + "sender: " + printObject(channelMessage.sender) + "\n");
            channelMessages.val(channelMessages.val() + "time: " + channelMessage.sendTime + "\n");
            channelMessages.val(channelMessages.val() + "id: " + channelMessage.messageId + "\n");
            channelMessages.val(channelMessages.val() + "body: " + channelMessage.body + "\n");
            channelMessages.val(channelMessages.val() + "mentioned" + filtersToAccountList(channelMessage.mentioned) + "\n");
            channelMessages.val(channelMessages.val() + "------------------------" + "\n");
        }
    );
});