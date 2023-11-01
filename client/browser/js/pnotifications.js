function handleCreated(tab) {
  console.log(tab.id);
}

$(document).ready(function () {
  $.ajax({
    contentType: 'application/json',
    data: JSON.stringify({
      "client": {
        "workSpaceId": "workSpaceMain",
        "accountId": "clientWeb1@test.ee",
        "deviceId": "311aae05-bade-48bf-b390-47a93a66c89e",
        "applicationId": "MLA_JAVA_HEADLESS"
      }
    }),
    dataType: 'json',
    success: function (data) {
      var wsUrl
      $.each(data, function (index, element) {
        if (index === "externalAdvertisedUrl") {
          wsUrl = element
        }
      });
      console.log("Ws connection url was aquired: " + wsUrl);

      //alert(wsUrl)
      let ws = new WebSocket(wsUrl);
      if (ws) {
        var intervalId = window.setInterval(function () {
          ws.send(JSON.stringify({"command": "PING"}));
        }, 20000);
        ws.onopen = function () {
          console.log('open');
        };

        ws.onmessage = function (e) {
          console.log('message', e.data);
          if (e.data != "PONG") {
            alert(e.data);
          }
        };

        ws.onerror = function (error) {
          alert("There was an error with your websocket!");
        };

        ws.onclose = function (event) {
          if (event.wasClean) {
            alert(
                `[close] Connection closed cleanly, code=${event.code} reason=${event.reason}`);
          } else {
            // e.g. server process killed or network down
            // event.code is usually 1006 in this case
            alert('[close] Connection died');
          }
        };
      }
    },
    error: function () {
      console.log("Attempt to aquare ws connection url failed");
    },
    processData: false,
    type: 'POST',
    url: 'http://95.217.166.42:8050/pushca/open-connection'
//    url: 'http://localhost:8080/open-connection'
//    url: 'https://app-rc.multiloginapp.net/open-connection'
  });
});