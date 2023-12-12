package core

import (
	"bytes"
	"encoding/json"
	"github.com/gorilla/websocket"
	"io"
	"log"
	"net/http"
	"pushca-client/model"
	modelrequest "pushca-client/model/request"
	modelresponse "pushca-client/model/response"
	"strings"
)

type PushcaWebSocket struct {
	PushcaApiUrl string
	PusherId     string
	Client       model.PClient
	WsUrl        string
	Token        string
	Connection   *websocket.Conn
}

func (wsPushca *PushcaWebSocket) GetInfo() string {
	jsonStr, errMarshal := json.Marshal(wsPushca.Client)
	if errMarshal != nil {
		log.Printf("Unable to marshal open connection request due to %s\n", errMarshal)
		return "UNKNOWN"
	}
	return string(jsonStr)
}
func (wsPushca *PushcaWebSocket) openConnection() (*websocket.Conn, error) {
	openConnectionRequest := &modelrequest.OpenConnectionRequest{
		Client: wsPushca.Client,
	}
	jsonData, errMarshal := json.Marshal(openConnectionRequest)
	if errMarshal != nil {
		log.Printf("Unable to marshal open connection request due to %s\n", errMarshal)
		return nil, errMarshal
	}

	request, errHttp := http.NewRequest("POST", wsPushca.PushcaApiUrl, bytes.NewBuffer(jsonData))
	request.Header.Set("Content-Type", "application/json; charset=UTF-8")
	request.Header.Set("User-Agent", "Mozilla")
	request.Header.Set("Accept", "application/json")

	httpClient := &http.Client{}
	httpResponse, errHttp := httpClient.Do(request)
	if errHttp != nil {
		log.Printf("Unable to send http post due to %s", errHttp)
		return nil, errHttp
	}

	//fmt.Println("response Status:", httpResponse.Status)
	//fmt.Println("response Headers:", response.Header)
	body, _ := io.ReadAll(httpResponse.Body)

	var ocResponse modelresponse.OpenConnectionResponse
	errUnmarshal := json.Unmarshal(body, &ocResponse)
	if errUnmarshal != nil {
		log.Printf("Unable to marshal JSON due to %s", errUnmarshal)
		return nil, errUnmarshal
	}
	ocResponse.LogAsString()
	wsPushca.PusherId = ocResponse.PusherInstanceId
	wsPushca.WsUrl = ocResponse.ExternalAdvertisedUrl
	lastSlashIndex := strings.LastIndex(wsPushca.WsUrl, "/")
	if lastSlashIndex != -1 && lastSlashIndex < len(wsPushca.WsUrl)-1 {
		wsPushca.Token = wsPushca.WsUrl[lastSlashIndex+1:]
		//log.Printf("Token was successfully extracted %s", wsPushca.Token)
	} else {
		log.Print("No token found")
	}

	conn, _, errWs := websocket.DefaultDialer.Dial(wsPushca.WsUrl, nil)
	if errWs != nil {
		log.Printf("Unable to open web socket connection due to %s", errWs)
		return nil, errWs
	}
	wsPushca.Connection = conn
	return wsPushca.Connection, nil
}

func (wsPushca *PushcaWebSocket) CloseConnection() error {
	err := wsPushca.Connection.Close()
	if err != nil {
		log.Printf("Ws connection was closed with error: %s", err)
		return err
	}
	return nil
}

func InitWebSocket(ws WebSocketApi, done chan struct{}) {
	conn, err := ws.openConnection()
	if err != nil {
		recover()
	}
	go func() {
		defer close(done)
		for {
			_, message, err := conn.ReadMessage()
			if err != nil {
				log.Println("read:", err)
				return
			}
			log.Printf("%s recv: %s", ws.GetInfo(), message)
		}
	}()
}
