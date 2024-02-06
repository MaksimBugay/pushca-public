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

function releaseWaiterWithSuccess(waiter, response) {
    waiter.resolve(new WaiterResponse(ResponseType.SUCCESS, response))
}

function releaseWaiterWithError(waiter, error) {
    waiter.reject(new WaiterResponse(ResponseType.ERROR, error));
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

PushcaClient.openConnection = function (clientObj, onOpenHandler, onCloseHandler, onMessageHandler) {
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
                    if (parts[1] === MessageType.RESPONSE) {
                        let body;
                        if (parts.length > 2) {
                            body = parts[2];
                        }
                        PushcaClient.releaseWaiterIfExists(parts[0], body)
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
