package main

import (
	"flag"
	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"log"
	"os"
	"os/signal"
	"pushca-client/core"
	"pushca-client/model"
	"time"
)

func main() {
	deviceId, errRandomUuid := uuid.NewRandom()
	if errRandomUuid != nil {
		log.Fatalf("cannot generate device Id due to %s", errRandomUuid)
	}

	httpPostUrl := "https://app-rc.multiloginapp.net/pushca/open-connection"
	//httpPostUrl := "http://push-app-rc.multiloginapp.net:8050/open-connection"
	//httpPostUrl := "http://localhost:8080/open-connection"

	pushcaWebSocket0 := &core.PushcaWebSocket{
		PushcaApiUrl: httpPostUrl,
		Client: model.PClient{
			WorkSpaceId:   "workSpaceMain",
			AccountId:     "client0@test.ee",
			DeviceId:      deviceId.String(),
			ApplicationId: "PUSHCA_CLIENT",
		},
	}
	log.Printf("Pusher instance id: %v", pushcaWebSocket0.PusherId)
	log.Printf("Token: %v", pushcaWebSocket0.Token)
	pushcaWebSocket1 := &core.PushcaWebSocket{
		PushcaApiUrl: httpPostUrl,
		Client: model.PClient{
			WorkSpaceId:   "workSpaceMain",
			AccountId:     "client1@test.ee",
			DeviceId:      "web-browser",
			ApplicationId: "PUSHCA_CLIENT",
		},
	}
	//================================================================================
	flag.Parse()
	log.SetFlags(0)

	interrupt := make(chan os.Signal, 1)
	signal.Notify(interrupt, os.Interrupt)
	done := make(chan struct{})

	errWsOpen := pushcaWebSocket0.OpenConnection(done)
	if errWsOpen != nil {
		log.Fatalf("cannot open web socket connection: client %s, error %s",
			errWsOpen, pushcaWebSocket0.GetInfo())
	}
	defer func(ws core.WebSocketApi) {
		err := ws.CloseConnection()
		if err != nil {
			log.Fatal("Ws connection was closed with error:", err)
		}
	}(pushcaWebSocket0)

	errWsOpen = pushcaWebSocket1.OpenConnection(done)
	if errWsOpen != nil {
		log.Fatalf("cannot open web socket connection: client %s, error %s",
			errWsOpen, pushcaWebSocket1.GetInfo())
	}
	defer func(ws core.WebSocketApi) {
		err := ws.CloseConnection()
		if err != nil {
			log.Fatal("Ws connection was closed with error:", err)
		}
	}(pushcaWebSocket1)

	pushcaWebSocket0.SendMessageWithAcknowledge4("1", pushcaWebSocket1.Client, false, "test message")
	clientFilter := model.ClientFilter{
		WorkSpaceID: "workSpaceMain",
	}
	exclude := make([]model.PClient, 0)
	exclude = append(exclude, pushcaWebSocket1.Client)
	clientFilterWithExclude := model.ClientFilter{
		WorkSpaceID: "workSpaceMain",
		Exclude:     exclude,
	}
	pushcaWebSocket0.BroadcastMessage2(clientFilter, "Very broad message")
	pushcaWebSocket1.BroadcastMessage4("2", clientFilterWithExclude, true, "message not for client 1")
	pushcaWebSocket0.SendMessage4("3", pushcaWebSocket1.Client, true, "message for client 1")
	pushcaWebSocket1.SendMessage2(pushcaWebSocket0.Client, "message for client 0")

	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()

	for {
		select {
		case <-done:
			return
		case <-ticker.C:
			//pushcaWebSocket0.PingServer()
			//pushcaWebSocket1.PingServer()
		case <-interrupt:
			log.Println("interrupt")

			// Cleanly close the connection by sending a close message and then
			// waiting (with timeout) for the server to close the connection.
			err0 := pushcaWebSocket0.Connection.WriteMessage(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""))
			if err0 != nil {
				log.Println("WS was closed with error:", err0)
			}
			err1 := pushcaWebSocket1.Connection.WriteMessage(websocket.CloseMessage, websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""))
			if err1 != nil {
				log.Println("WS was closed with error:", err1)
			}
			select {
			case <-done:
			case <-time.After(time.Second):
			}
			return
		}
	}
}