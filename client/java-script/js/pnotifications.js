const requiredClientFields = ['workSpaceId', 'accountId', 'deviceId', 'applicationId'];

const Command = Object.freeze({
    PING: "PING",
    ACKNOWLEDGE: "ACKNOWLEDGE",
    SEND_MESSAGE: "SEND_MESSAGE",
    SEND_MESSAGE_WITH_ACKNOWLEDGE: "SEND_MESSAGE_WITH_ACKNOWLEDGE",
    ADD_MEMBERS_TO_CHANNEL: "ADD_MEMBERS_TO_CHANNEL",
    SEND_MESSAGE_TO_CHANNEL: "SEND_MESSAGE_TO_CHANNEL",
    GET_CHANNEL_HISTORY: "GET_CHANNEL_HISTORY",
    REMOVE_ME_FROM_CHANNEL: "REMOVE_ME_FROM_CHANNEL",
    GET_CHANNELS: "GET_CHANNELS",
    GET_CHANNELS_PUBLIC_INFO: "GET_CHANNELS_PUBLIC_INFO",
    MARK_CHANNEL_AS_READ: "MARK_CHANNEL_AS_READ"
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

class PChannel {
    constructor(id, name) {
        this.id = id;
        this.name = name;
    }
}

class ClientFilter {
    constructor(workSpaceId, accountId, deviceId, applicationId) {
        this.workSpaceId = workSpaceId;
        this.accountId = accountId;
        this.deviceId = deviceId;
        this.applicationId = applicationId;
    }
}

class ChannelMember {
    constructor(workSpaceId, accountId, deviceId, applicationId, active) {
        this.workSpaceId = workSpaceId;
        this.accountId = accountId;
        this.deviceId = deviceId;
        this.applicationId = applicationId;
        this.active = active;
    }

    shortPrint() {
        if (isNotEmpty(this.active)) {
            return this.accountId + "[+]";
        } else {
            return this.accountId + "[-]";
        }
    }
}

class ChannelWithInfo {
    constructor(channel, members, counter, time, read) {
        this.channel = channel;
        this.members = members;
        this.counter = counter;
        this.time = time;
        this.read = read;
    }

    static fromObject(channelObj) {
        const channel = new PChannel(channelObj.channel.id, channelObj.channel.name);
        let members = [];
        if (channelObj.members) {
            members = channelObj.members.map(obj => new ChannelMember(
                    obj.workSpaceId,
                    obj.accountId,
                    obj.deviceId,
                    obj.applicationId,
                    obj.active
                )
            );
        }
        return new ChannelWithInfo(channel, members, channelObj.counter, channelObj.time, channelObj.read);
    }
}

class ChannelsResponse {
    constructor(channels) {
        this.channels = channels;
    }

    static fromWsResponse(jsonString) {
        const jsonObject = typeof jsonString === 'string' ? JSON.parse(jsonString) : jsonString;
        const channels = jsonObject.body.channels.map(obj => ChannelWithInfo.fromObject(obj));
        return new ChannelsResponse(channels);
    }
}

class HistoryPage {
    constructor(messages, offset, latest, more) {
        this.messages = messages;
        this.offset = offset;
        this.latest = latest;
        this.more = more;
    }

    static fromWsResponse(jsonString) {
        const jsonObject = typeof jsonString === 'string' ? JSON.parse(jsonString) : jsonString;
        const messages = jsonObject.body.messages.map(obj => ChannelMessage.fromObject(obj));
        return new HistoryPage(messages, jsonObject.body.offset, jsonObject.body.latest, jsonObject.body.more);
    }
}

class MessageDetails {
    constructor(id) {
        this.id = id;
    }

    static fromWsResponse(jsonString) {
        const jsonObject = typeof jsonString === 'string' ? JSON.parse(jsonString) : jsonString;
        const messageId = jsonObject.body.messageId;
        return new MessageDetails(messageId);
    }
}

class ChannelEvent {

    constructor(type, actor, channelId, filters, time) {
        this.type = type;
        this.actor = actor;
        this.channelId = channelId;
        this.filters = filters;
        this.time = time;
    }

    static fromJSON(jsonString) {
        const jsonObject = typeof jsonString === 'string' ? JSON.parse(jsonString) : jsonString;
        const actor = new ClientFilter(
            jsonObject.actor.workSpaceId,
            jsonObject.actor.accountId,
            jsonObject.actor.deviceId,
            jsonObject.actor.applicationId
        );
        let filters = [];
        if (jsonObject.filters) {
            filters = jsonObject.filters.map(obj => new ClientFilter(
                    obj.workSpaceId,
                    obj.accountId,
                    obj.deviceId,
                    obj.applicationId
                )
            );
        }
        return new ChannelEvent(jsonObject.type, actor, jsonObject.channelId, filters, jsonObject.time);
    }
}

class ChannelMessage {

    constructor(sender, channelId, messageId, parentId, sendTime, body, mentioned) {
        this.sender = sender;
        this.channelId = channelId;
        if (messageId) {
            this.messageId = messageId;
        } else {
            this.messageId = crypto.randomUUID().toString();
        }
        this.parentId = parentId;
        if (sendTime) {
            this.sendTime = sendTime;
        } else {
            this.sendTime = Date.now();
        }
        this.body = body;
        this.mentioned = mentioned;
    }

    static fromObject(jsonObject) {
        const sender = new ClientFilter(
            jsonObject.sender.workSpaceId,
            jsonObject.sender.accountId,
            jsonObject.sender.deviceId,
            jsonObject.sender.applicationId
        );
        let mentioned = [];
        if (isArrayNotEmpty(jsonObject.mentioned)) {
            mentioned = jsonObject.mentioned.map(obj => new ClientFilter(
                    obj.workSpaceId,
                    obj.accountId,
                    obj.deviceId,
                    obj.applicationId
                )
            );
        }
        return new ChannelMessage(sender, jsonObject.channelId, jsonObject.messageId, jsonObject.parentId,
            jsonObject.sendTime, jsonObject.body, mentioned);
    }

    static fromJSON(jsonString) {
        const jsonObject = typeof jsonString === 'string' ? JSON.parse(jsonString) : jsonString;
        return this.fromObject(jsonObject);
    }
}

class WaiterResponse {
    constructor(type, body) {
        this.type = type;
        this.body = body;
    }
}

class Waiter {
    constructor() {
        this.promise = new Promise((resolve, reject) => {
            this.resolve = resolve; // Assign resolve function to the outer scope variable
            this.reject = reject; // Assign reject function to the outer scope variable
        });
    }
}

function printDateTime(dt) {
    const dateTime = new Date(dt);
    const year = dateTime.getFullYear();
    const month = String(dateTime.getMonth() + 1).padStart(2, '0'); // Months are 0-indexed, add 1 to get the correct month
    const day = String(dateTime.getDate()).padStart(2, '0');
    const hours = String(dateTime.getHours()).padStart(2, '0');
    const minutes = String(dateTime.getMinutes()).padStart(2, '0');

    return `${year}-${month}-${day} ${hours}:${minutes}`;
}

function printObject(obj) {
    return Object.values(obj)
        .filter(value => value !== undefined && value !== null)
        .join('/');
}

function isArrayNotEmpty(arr) {
    return arr !== null && arr !== undefined && Array.isArray(arr) && arr.length > 0;
}

function isNotEmpty(x) {
    return x !== null && x !== undefined && x !== ''
}

function releaseWaiterWithSuccess(waiter, response) {
    waiter.resolve(new WaiterResponse(ResponseType.SUCCESS, response));
}

function releaseWaiterWithError(waiter, error) {
    waiter.reject(new WaiterResponse(ResponseType.ERROR, error));
}

function allClientFieldsAreNotEmpty(obj) {
    return requiredClientFields.every(field => {
        return obj.hasOwnProperty(field) && obj[field] !== null && obj[field] !== undefined && obj[field] !== '';
    });
}

class CommandWithId {
    constructor(id, message) {
        this.id = id;
        this.message = message;
    }
}

let PushcaClient = {};
PushcaClient.waitingHall = new Map();
PushcaClient.serverBaseUrl = 'http://localhost:8080'

PushcaClient.addToWaitingHall = function (id) {
    let waiter = new Waiter();
    PushcaClient.waitingHall.set(id, waiter);
    return waiter.promise;
}

PushcaClient.releaseWaiterIfExists = function (id, response) {
    let waiter = PushcaClient.waitingHall.get(id);
    if (waiter) {
        releaseWaiterWithSuccess(waiter, response)
        PushcaClient.waitingHall.delete(id);
    }
}
PushcaClient.executeWithRepeatOnFailure = async function (id, commandWithId, inTimeoutMs, numberOfRepeatAttempts) {
    if (PushcaClient.ws.readyState === WebSocket.OPEN) {
        console.log('WebSocket is open. All good');
    } else {
        console.log('WebSocket is not open. State:', PushcaClient.ws.readyState);
        return;
    }
    let n = numberOfRepeatAttempts || 3
    let result;
    for (let i = 0; i < n; i++) {
        result = await PushcaClient.execute(id, commandWithId, inTimeoutMs);
        if (ResponseType.SUCCESS === result.type) {
            break;
        }
    }
    return result;
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
        result = new WaiterResponse(ResponseType.ERROR, error)
    }
    console.log(result);
    return result;
}

PushcaClient.buildCommandMessage = function (command, args) {
    let id = crypto.randomUUID();
    let message = `${id}${MessagePartsDelimiter}${command}${MessagePartsDelimiter}${JSON.stringify(args)}`;
    return new CommandWithId(id, message);
}

PushcaClient.openConnection = function (baseUrl, clientObj, onOpenHandler, onCloseHandler, onMessageHandler,
                                        onChannelEventHandler, onChannelMessageHandler) {
    PushcaClient.serverBaseUrl = baseUrl;
    let requestObj = {};
    PushcaClient.ClientObj = clientObj;
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
                    if (typeof onOpenHandler === 'function') {
                        onOpenHandler(PushcaClient.ws);
                    }
                };

                PushcaClient.ws.onmessage = function (event) {
                    console.log('message', event.data);
                    let parts = event.data.split(MessagePartsDelimiter);
                    if (parts[1] === MessageType.ACKNOWLEDGE) {
                        PushcaClient.releaseWaiterIfExists(parts[0], null);
                        return;
                    }
                    if (parts[1] === MessageType.RESPONSE) {
                        let body;
                        if (parts.length > 2) {
                            body = parts[2];
                        }
                        PushcaClient.releaseWaiterIfExists(parts[0], body);
                        return;
                    }
                    if (parts[1] === MessageType.CHANNEL_EVENT) {
                        if (typeof onChannelEventHandler === 'function') {
                            onChannelEventHandler(ChannelEvent.fromJSON(parts[2]))
                        }
                        return;
                    }
                    if (parts[1] === MessageType.CHANNEL_MESSAGE) {
                        if (typeof onChannelMessageHandler === 'function') {
                            onChannelMessageHandler(ChannelMessage.fromJSON(parts[2]))
                        }
                        return;
                    }
                    if (parts.length === 2) {
                        PushcaClient.sendAcknowledge(parts[0]);
                        if (typeof onMessageHandler === 'function') {
                            onMessageHandler(PushcaClient.ws, parts[1]);
                        }
                        return;
                    }
                    if (typeof onMessageHandler === 'function') {
                        onMessageHandler(PushcaClient.ws, event.data);
                    }
                };

                PushcaClient.ws.onerror = function (error) {
                    console.log("There was an error with your websocket!");
                };

                PushcaClient.ws.onclose = function (event) {
                    if (event.wasClean) {
                        console.log(
                            `[close] Connection closed cleanly, code=${event.code} reason=${event.reason}`);
                    }
                    if (typeof onCloseHandler === 'function') {
                        onCloseHandler(PushcaClient.ws, event)
                    }
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

/**
 * acknowledge Pushca about received message (Pushca forwards acknowledge to sender)
 *
 * @param id - message id
 */
PushcaClient.sendAcknowledge = function (id) {
    let metaData = {};
    metaData["messageId"] = id;
    let commandWithId = PushcaClient.buildCommandMessage(Command.ACKNOWLEDGE, metaData);
    PushcaClient.ws.send(commandWithId.message);
}

/**
 * Send message to all connected clients that met the filtering requirements
 *
 * @param id            - message id (if null then will be assigned by Pushca)
 * @param dest          - filter of receivers
 * @param preserveOrder - keep sending order during delivery
 * @param message       - message text
 */
PushcaClient.broadcastMessage = async function (id, dest, preserveOrder, message) {
    let metaData = {};
    metaData["id"] = id;
    metaData["filter"] = dest;
    metaData["sender"] = PushcaClient.client;
    metaData["message"] = message;
    metaData["preserveOrder"] = preserveOrder;

    let commandWithId = PushcaClient.buildCommandMessage(Command.SEND_MESSAGE, metaData);
    let result = await PushcaClient.executeWithRepeatOnFailure(null, commandWithId)
    if (ResponseType.ERROR === result.type) {
        console.log("Failed broadcast message attempt: " + result.body.message);
    }
}

/**
 * send message to some client and wait for acknowledge, if no acknowledge after defined number of
 * send attempts then throw exception
 *
 * @param id            - message id (if null then will be assigned by Pushca)
 * @param dest          - client who should receive a message
 * @param preserveOrder - keep sending order during delivery
 * @param message       - message text
 */
PushcaClient.sendMessageWithAcknowledge = async function (id, dest, preserveOrder, message) {
    if (!allClientFieldsAreNotEmpty(dest)) {
        console.log("Cannot broadcast with acknowledge: " + JSON.stringify(dest));
        return;
    }
    let metaData = {};
    metaData["id"] = id;
    metaData["client"] = dest;
    metaData["sender"] = PushcaClient.client;
    metaData["message"] = message;
    metaData["preserveOrder"] = preserveOrder;

    let commandWithId = PushcaClient.buildCommandMessage(Command.SEND_MESSAGE_WITH_ACKNOWLEDGE, metaData);
    let result = await PushcaClient.executeWithRepeatOnFailure(id, commandWithId)
    if (ResponseType.ERROR === result.type) {
        console.log("Failed send message with acknowledge attempt: " + result.body.message);
    }
}

/**
 * Add new members into create if not exists channel
 *
 * @param channel - channel object
 * @param filters - new members
 */
PushcaClient.addMembersToChannel = async function (channel, filters) {
    let metaData = {};
    metaData["channel"] = channel;
    metaData["filters"] = filters;
    let commandWithId = PushcaClient.buildCommandMessage(Command.ADD_MEMBERS_TO_CHANNEL, metaData);
    let result = await PushcaClient.executeWithRepeatOnFailure(null, commandWithId)
    if (ResponseType.ERROR === result.type) {
        console.log("Failed add members to channel attempt: " + result.body.message);
    }
}

PushcaClient.sendMessageToChannel = async function (channel, mentioned, message) {
    let metaData = {};
    metaData["channel"] = channel;
    if (isArrayNotEmpty(mentioned)) {
        metaData["mentioned"] = mentioned;
    }
    metaData["message"] = message;
    let commandWithId = PushcaClient.buildCommandMessage(Command.SEND_MESSAGE_TO_CHANNEL, metaData);
    let result = await PushcaClient.executeWithRepeatOnFailure(null, commandWithId)
    if (ResponseType.ERROR === result.type) {
        console.log("Failed send message to channel attempt: " + result.body.message);
    }
    return MessageDetails.fromWsResponse(result.body);
}

PushcaClient.getChannelHistory = async function (channel, offset) {
    let metaData = {};
    metaData["channel"] = channel;
    if (offset) {
        metaData["offset"] = offset;
    }
    let commandWithId = PushcaClient.buildCommandMessage(Command.GET_CHANNEL_HISTORY, metaData);
    let result = await PushcaClient.executeWithRepeatOnFailure(null, commandWithId)
    if (ResponseType.ERROR === result.type) {
        console.log("Failed get channel history attempt: " + result.body.message);
    }
    return HistoryPage.fromWsResponse(result.body);
}

PushcaClient.removeMeFromChannel = async function (channel) {
    let metaData = {};
    metaData["channel"] = channel;
    let commandWithId = PushcaClient.buildCommandMessage(Command.REMOVE_ME_FROM_CHANNEL, metaData);
    let result = await PushcaClient.executeWithRepeatOnFailure(null, commandWithId)
    if (ResponseType.ERROR === result.type) {
        console.log("Failed remove me from channel attempt: " + result.body.message);
    }
}

PushcaClient.getChannels = async function (filter) {
    let metaData = {};
    metaData["filter"] = filter;
    let commandWithId = PushcaClient.buildCommandMessage(Command.GET_CHANNELS, metaData);
    let result = await PushcaClient.executeWithRepeatOnFailure(null, commandWithId)
    if (ResponseType.ERROR === result.type) {
        console.log("Failed get channels attempt: " + result.body.message);
    }
    return ChannelsResponse.fromWsResponse(result.body);
}

PushcaClient.getChannelsPublicInfo = async function (ids) {
    let metaData = {};
    metaData["ids"] = ids;
    let commandWithId = PushcaClient.buildCommandMessage(Command.GET_CHANNELS_PUBLIC_INFO, metaData);
    let result = await PushcaClient.executeWithRepeatOnFailure(null, commandWithId)
    if (ResponseType.ERROR === result.type) {
        console.log("Failed get channels public info attempt: " + result.body.message);
    }
    return ChannelsResponse.fromWsResponse(result.body);
}

PushcaClient.markChannelAsRead = async function (channel) {
    let metaData = {};
    metaData["channel"] = channel;
    let commandWithId = PushcaClient.buildCommandMessage(Command.MARK_CHANNEL_AS_READ, metaData);
    let result = await PushcaClient.executeWithRepeatOnFailure(null, commandWithId)
    if (ResponseType.ERROR === result.type) {
        console.log("Failed mark channel as read attempt: " + result.body.message);
    }
}
