const requiredClientFields = ['workSpaceId', 'accountId', 'deviceId', 'applicationId'];

const Command = Object.freeze({
    PING: "PING",
    ACKNOWLEDGE: "ACKNOWLEDGE",
    SEND_MESSAGE: "SEND_MESSAGE",
    SEND_MESSAGE_WITH_ACKNOWLEDGE: "SEND_MESSAGE_WITH_ACKNOWLEDGE",
    ADD_MEMBERS_TO_CHANNEL: "ADD_MEMBERS_TO_CHANNEL"
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

class ChannelEvent {

    constructor(type, actor, channelId, filters) {
        this.type = type;
        this.actor = actor;
        this.channelId = channelId;
        this.filters = filters;
    }

    static fromJSON(jsonString) {
        const jsonObject = typeof jsonString === 'string' ? JSON.parse(jsonString) : jsonString;
        const actor = new ClientFilter(
            jsonObject.actor.workSpaceId,
            jsonObject.actor.accountId,
            jsonObject.actor.deviceId,
            jsonObject.actor.applicationId
        );
        const filters = jsonObject.filters.map(obj => new ClientFilter(
                obj.workSpaceId,
                obj.accountId,
                obj.deviceId,
                obj.applicationId
            )
        );
        return new ChannelEvent(jsonObject.type, actor, jsonObject.channelId, filters);
    }
}

class Person {
    constructor(name, age, address) {
        this.name = name;
        this.age = age;
        this.address = address; // Address is expected to be an instance of Address
    }

    greet() {
        console.log(`Hello, my name is ${this.name} and I am ${this.age} years old.`);
    }

    static fromJSON(jsonString) {
        const jsonObject = typeof jsonString === 'string' ? JSON.parse(jsonString) : jsonString;
        const address = new Address(jsonObject.address.street, jsonObject.address.city, jsonObject.address.zipCode);
        return new Person(jsonObject.name, jsonObject.age, address);
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

function getBrowserName() {
    const userAgent = navigator.userAgent;

    if (userAgent.match(/edg/i)) {
        return "Chrome";
    } else if (userAgent.match(/firefox|fxios/i)) {
        return "Firefox";
    } else if (userAgent.match(/safari/i)) {
        return "Safari";
    } else if (userAgent.match(/opr\//i)) {
        return "Opera";
    } else if (userAgent.match(/chrome|chromium|crios/i)) {
        return "Edge";
    } else if (userAgent.match(/msie|trident/i)) {
        return "Internet Explorer";
    } else {
        return "Unknown";
    }
}

function releaseWaiterWithSuccess(waiter, response) {
    waiter.resolve(new WaiterResponse(ResponseType.SUCCESS, response))
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
PushcaClient.serverBaseUrl = 'http://localhost:8050'

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

PushcaClient.openConnection = function (clientObj, onOpenHandler, onCloseHandler, onMessageHandler, onChannelEventHandler) {
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
                    onOpenHandler(PushcaClient.ws);
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
                        onChannelEventHandler(ChannelEvent.fromJSON(parts[2]))
                        return;
                    }
                    if (parts.length === 2) {
                        PushcaClient.sendAcknowledge(parts[0]);
                        onMessageHandler(PushcaClient.ws, parts[1]);
                        return;
                    }
                    onMessageHandler(PushcaClient.ws, event.data);
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
