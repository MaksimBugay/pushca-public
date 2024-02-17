function printListOfChannelMembers(members) {
    return members.map(member => member.shortPrint()).join(";");
}

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
    let channelEvents = $("textarea#p-channel-events");
    channelEvents.val("");
    let channelMessages = $("textarea#p-channel-messages");
    let history = $("textarea#p-history");
    history.val("");
    let myChannels = $("textarea#p-channels-with-info");
    let publicChannels = $("textarea#p-channels-public-info");
    let impressionStat = $("textarea#p-impression-stat");

    function printChannelMessage(channelMessage, withRefresh) {
        channelMessages.val(channelMessages.val() + "sender: " + printObject(channelMessage.sender) + "\n");
        channelMessages.val(channelMessages.val() + "time: " + printDateTime(channelMessage.sendTime) + "\n");
        channelMessages.val(channelMessages.val() + "id: " + channelMessage.messageId + "\n");
        channelMessages.val(channelMessages.val() + "parent-id: " + channelMessage.parentId + "\n");
        channelMessages.val(channelMessages.val() + "body: " + channelMessage.body + "\n");
        if (isArrayNotEmpty(channelMessage.mentioned)) {
            channelMessages.val(channelMessages.val() + "mentioned" + filtersToAccountList(channelMessage.mentioned) + "\n");
        }
        channelMessages.val(channelMessages.val() + "------------------------" + "\n");
        const textArea = document.getElementById("p-channel-messages");
        textArea.scrollTop = textArea.scrollHeight;

        const container = document.getElementById('comments-container');
        const commentElement = document.createElement('div');
        commentElement.className = 'comment-item';
        commentElement.innerHTML = `
          <div class="comment-content">
            <div class="comment-author-date">
                <div class="comment-author">${channelMessage.sender.accountId}</div>
                <div class="comment-date">${printDateTime(channelMessage.sendTime)}</div>
            </div>
            <div class="comment-text">${channelMessage.body}</div>
          </div>
        `;
        container.appendChild(commentElement);
        container.scrollTop = container.scrollHeight;
        if (withRefresh) {
            reloadMyChannels();
            reloadPublicChannels();
        }
    }

    function printChannelEvent(channelEvent, withRefresh) {
        channelEvents.val(channelEvents.val() + channelEvent.type + "\n");
        channelEvents.val(channelEvents.val() + channelEvent.channelId + "\n");
        channelEvents.val(channelEvents.val() + "time: " + printDateTime(channelEvent.time) + "\n");
        channelEvents.val(channelEvents.val() + "actor: " + printObject(channelEvent.actor) + "\n");
        if (isArrayNotEmpty(channelEvent.filters)) {
            channelEvents.val(channelEvents.val() + "members: " + filtersToAccountList(channelEvent.filters) + "\n");
        }
        channelEvents.val(channelEvents.val() + "------------------------" + "\n");
        const textArea = document.getElementById("p-channel-events");
        textArea.scrollTop = textArea.scrollHeight;

        if (withRefresh) {
            reloadMyChannels();
            reloadPublicChannels();
            reloadImpressionStat();
        }
    }

    function printChannelWithInfo(channelWithInfo) {
        myChannels.val(myChannels.val() + printObject(channelWithInfo.channel) + "\n");
        if (isArrayNotEmpty(channelWithInfo.members)) {
            myChannels.val(myChannels.val() + "members: " + printListOfChannelMembers(channelWithInfo.members) + "\n");
        }
        myChannels.val(myChannels.val() + "counter: " + channelWithInfo.counter + "\n");
        myChannels.val(myChannels.val() + "last updated: " + printDateTime(channelWithInfo.time) + "\n");
        myChannels.val(myChannels.val() + "read: " + channelWithInfo.read + "\n");
        myChannels.val(myChannels.val() + "------------------------" + "\n");
        const textArea = document.getElementById("p-channels-with-info");
        textArea.scrollTop = textArea.scrollHeight;
    }

    function printChannelsPublicInfo(channelWithInfo) {
        publicChannels.val(publicChannels.val() + printObject(channelWithInfo.channel) + "\n");
        publicChannels.val(publicChannels.val() + "counter: " + channelWithInfo.counter + "\n");
        publicChannels.val(publicChannels.val() + "last updated: " + printDateTime(channelWithInfo.time) + "\n");
        publicChannels.val(publicChannels.val() + "------------------------" + "\n");
        const textArea = document.getElementById("p-channels-with-info");
        textArea.scrollTop = textArea.scrollHeight;
    }

    function printImpressionStatItem(item) {
        impressionStat.val(impressionStat.val() + "resource id: " + item.resourceId + "\n");
        if (isArrayNotEmpty(item.counters)) {
            item.counters.forEach(counter => {
                impressionStat.val(impressionStat.val() + counter.shortPrint() + "\n");
            });
        }
        impressionStat.val(impressionStat.val() + "------------------------" + "\n");
        const textArea = document.getElementById("p-impression-stat");
        textArea.scrollTop = textArea.scrollHeight;
    }

    async function reloadMessagesFromHistory() {
        channelMessages.val("");
        let historyPage = await PushcaClient.getChannelHistory(channel);
        if (isArrayNotEmpty(historyPage.messages)) {
            historyPage.messages.forEach(channelMessage => {
                printChannelMessage(channelMessage, false);
            });
        }
        reloadMyChannels();
        reloadPublicChannels();
    }

    async function reloadPublicChannels() {
        publicChannels.val("");
        let ids = [channel.id];
        const channelsResponse = await PushcaClient.getChannelsPublicInfo(ids);
        if (isArrayNotEmpty(channelsResponse.channels)) {
            console.log(JSON.stringify(channelsResponse));
            channelsResponse.channels.forEach(channelWithInfo => {
                printChannelsPublicInfo(channelWithInfo);
            });
        }
    }

    async function reloadImpressionStat() {
        impressionStat.val("");
        let ids = [channel.id];
        const impressionsResponse = await PushcaClient.getImpressionStat(ids);
        if (isArrayNotEmpty(impressionsResponse.items)) {
            console.log(JSON.stringify(impressionsResponse));
            impressionsResponse.items.forEach(item => {
                printImpressionStatItem(item);
            });
        }
    }

    async function addMeToChannel() {
        let filterObj = new ClientFilter(
            PushcaClient.ClientObj.workSpaceId,
            PushcaClient.ClientObj.accountId,
            null,
            PushcaClient.ClientObj.applicationId
        );
        PushcaClient.addMembersToChannel(channel, [filterObj]);
    }

    async function reloadMyChannels() {
        let filterObj = new ClientFilter(
            PushcaClient.ClientObj.workSpaceId,
            PushcaClient.ClientObj.accountId,
            null,
            PushcaClient.ClientObj.applicationId
        );
        const channelsResponse = await PushcaClient.getChannels(filterObj);
        myChannels.val("");
        if (isArrayNotEmpty(channelsResponse.channels)) {
            console.log(JSON.stringify(channelsResponse));
            channelsResponse.channels.forEach(channelWithInfo => {
                printChannelWithInfo(channelWithInfo);
            });
        }
    }

    let channel = new PChannel("CH12345", "test-channel");

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
        addMeToChannel();
    });

    $("#p-reload-from-history").click(async function () {
        await reloadMessagesFromHistory();
    });

    $("#p-send-message-to-channel").click(async function () {
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
        let messageBody = $('#p-message').val();
        const messageDetails = await PushcaClient.sendMessageToChannel(channel, [filterObj2, filterObj3], messageBody);
        if (messageDetails.id) {
            let channelMessage = new ChannelMessage(
                PushcaClient.ClientObj, channel.id, messageDetails.id, null, null,
                messageBody, [filterObj2, filterObj3]);
            printChannelMessage(channelMessage, true);
            await reloadMyChannels();
            await reloadPublicChannels();
        }
    });

    $("#p-remove-me-from-channel").click(async function () {
        await PushcaClient.removeMeFromChannel(channel);
        await reloadMyChannels();
    });

    $("#p-get-my-channels").click(async function () {
        await reloadMyChannels();
    });

    $("#p-get-channels-public-info").click(async function () {
        await reloadPublicChannels();
    });
    $("#p-mark-channel-as-read").click(async function () {
        await PushcaClient.markChannelAsRead(channel);
        await reloadMyChannels();
    });
    $("#p-get-impression-stat").click(async function () {
        await reloadImpressionStat();
    });
    $("#p-add-impression").click(async function () {
        const impression = new PImpression(channel.id, ResourceType.CHANNEL, 7);
        await PushcaClient.addImpression(channel, impression);
        await reloadImpressionStat();
    });

    $("#p-remove-impression").click(async function () {
        const impression = new PImpression(channel.id, ResourceType.CHANNEL, 7);
        await PushcaClient.removeImpression(channel, impression);
        await reloadImpressionStat();
    });
    let accountId = getQueryParam("account-id");
    if (!isNotEmpty(accountId)) {
        accountId = "clientWeb" + Date.now();
    }
    let deviceId = getQueryParam("device-id");
    if (!isNotEmpty(deviceId)) {
        deviceId = crypto.randomUUID().toString();
    }
    let clientObj = new ClientFilter(
        "workSpaceMain",
        accountId,
        deviceId,
        "MLA_JAVA_HEADLESS"
    );

    $("#l-client").text(printObject(clientObj));

    PushcaClient.openWsConnection('wss://ec2-13-51-172-15.eu-north-1.compute.amazonaws.com:35085/', clientObj,
        function (ws) {
            PushcaClient.PingIntervalId = window.setInterval(function () {
                PushcaClient.ws.send(JSON.stringify({"command": "PING"}));
            }, 20000);
            addMeToChannel();
            reloadMessagesFromHistory();
            reloadMyChannels();
            reloadPublicChannels();
            reloadImpressionStat();
        },
        function (ws, event) {
            window.clearInterval(PushcaClient.PingIntervalId);
            if (!event.wasClean) {
                $("#l-message").text("Your connection died, refresh the page please");
            }
        },
        function (ws, messageText) {
            if (messageText !== "PONG") {
                history.val(history.val() + messageText + "\n");
            }
        },
        function (channelEvent) {
            printChannelEvent(channelEvent, true);
        },
        function (channelMessage) {
            printChannelMessage(channelMessage, true);
        }
    );
});

$(window).on('beforeunload', function () {
    PushcaClient.ws.close(1000, "leave");
});