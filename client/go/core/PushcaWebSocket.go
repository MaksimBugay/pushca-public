package core

import (
	"bytes"
	"encoding/json"
	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"io"
	"log"
	"net/http"
	"pushca-client/model"
	modelrequest "pushca-client/model/request"
	modelresponse "pushca-client/model/response"
	"pushca-client/util"
	"strings"
)

const (
	AcknowledgePrefix     = "ACKNOWLEDGE@@"
	TokenPrefix           = "TOKEN@@"
	BinaryManifestPrefix  = "BINARY_MANIFEST@@"
	MessagePartsDelimiter = "@@"
)

type PushcaWebSocket struct {
	PushcaApiUrl                         string
	PusherId                             string
	Client                               model.PClient
	WsUrl                                string
	Token                                string
	Connection                           *websocket.Conn
	MessageConsumer, AcknowledgeConsumer func(ws WebSocketApi, message string)
	BinaryManifestConsumer               func(ws WebSocketApi, message model.BinaryObjectData)
}

func (wsPushca *PushcaWebSocket) GetInfo() string {
	return wsPushca.Client.AccountId
}
func (wsPushca *PushcaWebSocket) GetFullInfo() string {
	jsonStr, errMarshal := json.Marshal(wsPushca.Client)
	if errMarshal != nil {
		log.Printf("Unable to marshal open connection request due to %s\n", errMarshal)
		return "UNKNOWN"
	}
	return string(jsonStr)
}
func (wsPushca *PushcaWebSocket) OpenConnection(done chan struct{}) error {
	openConnectionRequest := &modelrequest.OpenConnectionRequest{
		Client: wsPushca.Client,
	}
	jsonData, errMarshal := json.Marshal(openConnectionRequest)
	if errMarshal != nil {
		log.Printf("Unable to marshal open connection request due to %s\n", errMarshal)
		return errMarshal
	}

	request, errHttp := http.NewRequest("POST", wsPushca.PushcaApiUrl, bytes.NewBuffer(jsonData))
	request.Header.Set("Content-Type", "application/json; charset=UTF-8")
	request.Header.Set("User-Agent", "Mozilla")
	request.Header.Set("Accept", "application/json")

	httpClient := &http.Client{}
	httpResponse, errHttp := httpClient.Do(request)
	if errHttp != nil {
		log.Printf("Unable to send http post due to %s", errHttp)
		return errHttp
	}

	//fmt.Println("response Status:", httpResponse.Status)
	//fmt.Println("response Headers:", response.Header)
	body, _ := io.ReadAll(httpResponse.Body)

	var ocResponse modelresponse.OpenConnectionResponse
	errUnmarshal := json.Unmarshal(body, &ocResponse)
	if errUnmarshal != nil {
		log.Printf("Unable to marshal JSON due to %s", errUnmarshal)
		return errUnmarshal
	}
	ocResponse.LogAsString()
	wsPushca.PusherId = ocResponse.PusherInstanceId
	wsPushca.WsUrl = ocResponse.ExternalAdvertisedUrl
	//wsPushca.WsUrl = ocResponse.BrowserAdvertisedUrl
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
		return errWs
	}
	wsPushca.Connection = conn
	go func() {
		defer close(done)
		for {
			mType, message, err := conn.ReadMessage()
			if err != nil {
				log.Println("read:", err)
				return
			}
			if mType == websocket.TextMessage {
				wsPushca.processMessage(string(message))
			}
			if mType == websocket.BinaryMessage {
				log.Print("binary message was received")
			}
		}
	}()
	return nil
}

func (wsPushca *PushcaWebSocket) CloseConnection() error {
	err := wsPushca.Connection.Close()
	if err != nil {
		log.Printf("Ws connection was closed with error: %s", err)
		return err
	}
	return nil
}
func (wsPushca *PushcaWebSocket) PingServer() {
	command := model.CommandWithMetaData{
		Command: "PING",
	}
	errWs := wsPushca.Connection.WriteJSON(command)
	if errWs != nil {
		log.Printf("Cannot send PING to server: client %s, error %s", wsPushca.GetInfo(), errWs)
	}
}
func (wsPushca *PushcaWebSocket) SendMessageWithAcknowledge4(id string, dest model.PClient, preserveOrder bool, message string) {
	metaData := make(map[string]interface{})

	metaData["id"] = id
	metaData["client"] = dest
	metaData["sender"] = wsPushca.Client
	metaData["message"] = message
	metaData["preserveOrder"] = preserveOrder

	command := model.CommandWithMetaData{
		Command:  "SEND_MESSAGE_WITH_ACKNOWLEDGE",
		MetaData: metaData,
	}

	errWs := wsPushca.Connection.WriteJSON(command)
	if errWs != nil {
		log.Printf("Cannot send message: client %s, error %s", wsPushca.GetInfo(), errWs)
	}
}

func (wsPushca *PushcaWebSocket) SendMessageWithAcknowledge3(id string, dest model.PClient, message string) {
	wsPushca.SendMessageWithAcknowledge4(id, dest, false, message)
}

func (wsPushca *PushcaWebSocket) SendAcknowledge(id string) {
	metaData := make(map[string]interface{})
	metaData["messageId"] = id

	command := model.CommandWithMetaData{
		Command:  "ACKNOWLEDGE",
		MetaData: metaData,
	}

	errWs := wsPushca.Connection.WriteJSON(command)
	if errWs != nil {
		log.Printf("Cannot send acknowledge: client %s, error %s", wsPushca.GetInfo(), errWs)
	}
}

func (wsPushca *PushcaWebSocket) BroadcastMessage4(id string, dest model.ClientFilter, preserveOrder bool, message string) {
	metaData := make(map[string]interface{})

	metaData["id"] = id
	metaData["filter"] = dest
	metaData["sender"] = wsPushca.Client
	metaData["message"] = message
	metaData["preserveOrder"] = preserveOrder

	command := model.CommandWithMetaData{
		Command:  "SEND_MESSAGE",
		MetaData: metaData,
	}

	errWs := wsPushca.Connection.WriteJSON(command)
	if errWs != nil {
		log.Printf("Cannot broadcast message: client %s, error %s", wsPushca.GetInfo(), errWs)
	}
}

func (wsPushca *PushcaWebSocket) BroadcastMessage2(dest model.ClientFilter, message string) {
	wsPushca.BroadcastMessage4("", dest, false, message)
}

func (wsPushca *PushcaWebSocket) SendMessage4(id string, dest model.PClient, preserveOrder bool, message string) {
	clientFilter := model.ClientFilter{
		WorkSpaceID:   dest.WorkSpaceId,
		AccountID:     dest.AccountId,
		DeviceID:      dest.DeviceId,
		ApplicationID: dest.ApplicationId,
	}
	wsPushca.BroadcastMessage4(id, clientFilter, preserveOrder, message)
}

func (wsPushca *PushcaWebSocket) SendMessage2(dest model.PClient, message string) {
	wsPushca.SendMessage4("", dest, false, message)
}

func (wsPushca *PushcaWebSocket) processMessage(inMessage string) {
	if strings.TrimSpace(inMessage) == "" {
		return
	}
	message := inMessage
	if strings.HasPrefix(inMessage, AcknowledgePrefix) {
		if wsPushca.AcknowledgeConsumer != nil {
			wsPushca.AcknowledgeConsumer(
				wsPushca,
				strings.Replace(inMessage, AcknowledgePrefix, "", 1),
			)
		}
		return
	}
	if strings.HasPrefix(inMessage, TokenPrefix) {
		wsPushca.Token = strings.Replace(inMessage, TokenPrefix, "", 1)
		return
	}
	if strings.HasPrefix(inMessage, BinaryManifestPrefix) {
		manifestJSON := strings.Replace(inMessage, BinaryManifestPrefix, "", 1)

		var manifest model.BinaryObjectData
		errUnmarshal := json.Unmarshal([]byte(manifestJSON), &manifest)
		if errUnmarshal != nil {
			log.Printf("Broken binary manifest: client %s, error %s", wsPushca.GetInfo(), errUnmarshal)
			return
		}
		if wsPushca.BinaryManifestConsumer != nil {
			wsPushca.BinaryManifestConsumer(wsPushca, manifest)
		}
		return
	}
	if strings.Contains(inMessage, MessagePartsDelimiter) {
		parts := strings.SplitN(inMessage, MessagePartsDelimiter, 2)
		wsPushca.SendAcknowledge(parts[0])
		message = parts[1]
	}
	if wsPushca.MessageConsumer != nil {
		wsPushca.MessageConsumer(wsPushca, message)
	}
}

func (wsPushca *PushcaWebSocket) SendBinaryMessage4(dest model.PClient, message []byte,
	pId uuid.UUID, withAcknowledge bool) {
	id := pId
	if id == uuid.Nil {
		id = uuid.New()
	}
	var order int32
	order = 2147483647
	prefix := util.ToDatagramPrefix(id, order, dest.HashCode(), withAcknowledge)

	errWs := wsPushca.Connection.WriteMessage(websocket.BinaryMessage, append(prefix, message...))
	if errWs != nil {
		log.Printf("Cannot send bimary message: client %s, error %s", wsPushca.GetInfo(), errWs)
	}
}

func (wsPushca *PushcaWebSocket) SendBinaryMessage2(dest model.PClient, message []byte) {
	wsPushca.SendBinaryMessage4(dest, message, uuid.Nil, false)
}
