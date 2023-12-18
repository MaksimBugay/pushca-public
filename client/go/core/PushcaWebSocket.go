package core

import (
	"bytes"
	"encoding/json"
	"fmt"
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
	"sync"
)

const (
	AcknowledgePrefix     = "ACKNOWLEDGE@@"
	TokenPrefix           = "TOKEN@@"
	BinaryManifestPrefix  = "BINARY_MANIFEST@@"
	MessagePartsDelimiter = "@@"
	MaxInteger            = 2147483647
)

var binaries sync.Map

type PushcaWebSocket struct {
	PushcaApiUrl                         string
	PusherId                             string
	Client                               model.PClient
	WsUrl                                string
	Token                                string
	Connection                           *websocket.Conn
	MessageConsumer, AcknowledgeConsumer func(ws WebSocketApi, message string)
	BinaryManifestConsumer               func(ws WebSocketApi, message model.BinaryObjectData)
	BinaryMessageConsumer                func(ws WebSocketApi, message []byte)
	DataConsumer                         func(ws WebSocketApi, data model.Binary)
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
	defer closeHttpResponse(httpResponse)
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
				wsPushca.processBinary(message)
			}
		}
	}()
	return nil
}

func closeHttpResponse(response *http.Response) {
	if response != nil {
		if !response.Close {
			log.Printf("cannot close http response")
		}
	}
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

func (wsPushca *PushcaWebSocket) SendAcknowledge2(binaryID uuid.UUID, order int32) {
	id := fmt.Sprintf("%s-%d", binaryID, order)
	wsPushca.SendAcknowledge(id)
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
func (wsPushca *PushcaWebSocket) processBinary(inBinary []byte) {
	clientHash, errConversion := util.BytesToInt(inBinary[:4])
	if errConversion != nil {
		log.Printf("cannot convert to int from byte[]: client %s, error %s", wsPushca.GetInfo(), errConversion)
		return
	}
	if clientHash != wsPushca.Client.HashCode() { // Replace hash function with your PClient hashing logic
		log.Printf("Data was intended for another client: client %s", wsPushca.GetInfo())
		return
	}
	withAcknowledge, errConversion := util.BytesToBoolean(inBinary[4:5])
	if errConversion != nil {
		log.Printf("cannot convert to bool from byte[]: client %s, error %s", wsPushca.GetInfo(), errConversion)
		return
	}
	binaryID, errConversion := util.BytesToUUID(inBinary[5:21])
	if errConversion != nil {
		log.Printf("cannot convert to UUID from byte[]: client %s, error %s", wsPushca.GetInfo(), errConversion)
		return
	}
	order, errConversion := util.BytesToInt(inBinary[21:25])
	if errConversion != nil {
		log.Printf("cannot convert to int from byte[]: client %s, error %s", wsPushca.GetInfo(), errConversion)
		return
	}
	//binary message was received
	if MaxInteger == order {
		if wsPushca.BinaryMessageConsumer != nil {
			wsPushca.BinaryMessageConsumer(wsPushca, inBinary[25:])
		}
		return
	}
	//-----------------------------------------------------------------------------
	var binaryData model.BinaryObjectData
	value, exists := binaries.Load(binaryID)
	if exists {
		// If the binary ID exists, perform actions on the data
		binaryData = value.(model.BinaryObjectData)
		// Fill with received data (replace this with your logic)
		binaryData.FillWithReceivedData(order, inBinary[25:])
		// Store the updated data back in the map
		binaries.Store(binaryID, binaryData)
	} else {
		// If the binary ID doesn't exist, throw an error
		log.Printf("Unknown binary with id = %s", binaryID)
		return
	}
	datagram := binaryData.GetDatagram(order)
	if datagram == nil {
		log.Printf("Unknown datagram: binaryId=%v, order=%d", binaryID, order)
	}
	if withAcknowledge {
		wsPushca.SendAcknowledge2(binaryID, order)
	}
	if binaryData.IsCompleted() {
		if wsPushca.DataConsumer != nil {
			wsPushca.DataConsumer(wsPushca, model.ToBinary(&binaryData))
		}
		binaries.Delete(binaryID)
		log.Printf("Binary was successfully received: id=%v, name=%s", binaryID, binaryData.Name)
	}
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

		var binaryObjectData model.BinaryObjectData
		errUnmarshal := json.Unmarshal([]byte(manifestJSON), &binaryObjectData)
		if errUnmarshal != nil {
			log.Printf("Broken binary binaryObjectData: client %s, error %s", wsPushca.GetInfo(), errUnmarshal)
			return
		}
		binaries.Store(binaryObjectData.ID, binaryObjectData)
		if wsPushca.BinaryManifestConsumer != nil {
			wsPushca.BinaryManifestConsumer(wsPushca, binaryObjectData)
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
	order = MaxInteger
	prefix := util.ToDatagramPrefix(id, order, dest.HashCode(), withAcknowledge)

	errWs := wsPushca.Connection.WriteMessage(websocket.BinaryMessage, append(prefix, message...))
	if errWs != nil {
		log.Printf("Cannot send bimary message: client %s, error %s", wsPushca.GetInfo(), errWs)
	}
}

func (wsPushca *PushcaWebSocket) SendBinaryMessage2(dest model.PClient, message []byte) {
	wsPushca.SendBinaryMessage4(dest, message, uuid.Nil, false)
}
