package core

import (
	"bytes"
	"crypto/tls"
	"encoding/json"
	"errors"
	"github.com/google/uuid"
	"io"
	"log"
	"net/http"
	"pushca-client/model"
	modelrequest "pushca-client/model/request"
	modelresponse "pushca-client/model/response"
	"pushca-client/util"
	"strings"
	"sync"
	"time"
)

const (
	MessagePartsDelimiter  = "@@"
	DefaultResponse        = "SUCCESS"
	MaxInteger             = 2147483647
	AcknowledgeTimeout     = 10 * time.Second
	MaxRepeatAttemptNumber = 3
	PushcaTokenTtlSec      = 60 * 20
)

type PushcaWebSocket struct {
	PushcaApiUrl            string
	PusherId                string
	Client                  model.PClient
	WsBaseUrl               string
	Token                   string
	WebSocketFactory        func() WebSocketApi
	MessageConsumer         func(ws PushcaWebSocketApi, message string)
	BinaryManifestConsumer  func(ws PushcaWebSocketApi, message model.BinaryObjectData)
	BinaryMessageConsumer   func(ws PushcaWebSocketApi, message []byte)
	DataConsumer            func(ws PushcaWebSocketApi, data model.Binary)
	UnknownDatagramConsumer func(ws PushcaWebSocketApi, data model.UnknownDatagram)
	TlsConfig               *tls.Config
	Binaries                map[uuid.UUID]*model.BinaryObjectData
	AcknowledgeCallbacks    *sync.Map
	webSocket               WebSocketApi
	mutex                   sync.Mutex
	writeToSocketMutex      sync.Mutex
	done                    chan struct{}
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
func (wsPushca *PushcaWebSocket) Open(done chan struct{}) error {
	wsPushca.done = done
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
	wsUrl := ocResponse.ExternalAdvertisedUrl
	lastSlashIndex := strings.LastIndex(wsUrl, "/")
	if lastSlashIndex != -1 && lastSlashIndex < len(wsUrl)-1 {
		wsPushca.Token = wsUrl[lastSlashIndex+1:]
		wsPushca.WsBaseUrl = wsUrl[0 : lastSlashIndex+1]
		log.Printf("Token was successfully extracted: client %v", wsPushca.GetInfo())
	} else {
		log.Print("No token found")
	}
	err := wsPushca.OpenWebSocket()
	if err != nil {
		return err
	}
	go func() {
		pingInterval := 15
		ticker := time.NewTicker(time.Duration(pingInterval) * time.Second)
		defer ticker.Stop()
		errorCounter := 0
		for {
			select {
			case <-done:
				return
			case <-ticker.C:
				if wsPushca.webSocket.IsClosed() {
					errorCounter = errorCounter + 1
					if errorCounter > (PushcaTokenTtlSec / pingInterval) {
						_ = wsPushca.Open(wsPushca.done)
					} else {
						_ = wsPushca.OpenWebSocket()
					}
				} else {
					errorCounter = 0
					wsPushca.PingServer()
				}
				wsPushca.removeExpiredManifests()
			}
		}
	}()
	go func() {
		ticker := time.NewTicker(10 * time.Minute)
		defer ticker.Stop()
		for {
			select {
			case <-done:
				return
			case <-ticker.C:
				if !wsPushca.webSocket.IsClosed() {
					newToken := wsPushca.RefreshToken()
					if len(newToken) > 0 {
						wsPushca.Token = newToken
					}
				}
			}
		}
	}()
	return nil
}

func (wsPushca *PushcaWebSocket) OpenWebSocket() error {
	if wsPushca.webSocket == nil {
		wsPushca.webSocket = wsPushca.WebSocketFactory()
	}
	errWs := wsPushca.webSocket.Open(
		wsPushca.WsBaseUrl+wsPushca.Token,
		wsPushca.processMessage,
		wsPushca.processBinary,
		nil,
		wsPushca.TlsConfig,
		wsPushca.done,
	)
	if errWs != nil {
		log.Printf("Unable to open web socket connection due to %s", errWs)
		return errWs
	}
	return nil
}

func (wsPushca *PushcaWebSocket) Close() {
	wsPushca.webSocket.Close()
}

func closeHttpResponse(response *http.Response) {
	if response != nil {
		if !response.Close {
			log.Printf("cannot close http response")
		}
	}
}
func (wsPushca *PushcaWebSocket) RefreshToken() string {
	response, errWs := wsPushca.wsConnectionWriteCommand(RefreshToken, nil,
		true, "", nil)
	if errWs != nil {
		log.Printf("Cannot send refresh token request to server: client %s, error %s", wsPushca.GetInfo(), errWs)
		return ""
	}
	return response
}

func (wsPushca *PushcaWebSocket) PingServer() {
	_, errWs := wsPushca.wsConnectionWriteShortCommand(Ping, nil)
	if errWs != nil {
		log.Printf("Cannot send PING to server: client %s, error %s", wsPushca.GetInfo(), errWs)
	}
}

func (wsPushca *PushcaWebSocket) SendMessageWithAcknowledge4(msgID string, dest model.PClient, preserveOrder bool, message string) {
	metaData := make(map[string]interface{})

	id := msgID
	if len(id) == 0 {
		id = uuid.New().String()
	}

	metaData["id"] = id
	metaData["client"] = dest
	metaData["sender"] = wsPushca.Client
	metaData["message"] = message
	metaData["preserveOrder"] = preserveOrder

	_, err := wsPushca.wsConnectionWriteCommand(
		SendMessageWithAcknowledge,
		metaData, true, id,
		nil,
	)
	if err != nil {
		log.Printf("Cannot send message: client %s, error %s", wsPushca.GetInfo(), err)
	}
}

func (wsPushca *PushcaWebSocket) SendMessageWithAcknowledge3(id string, dest model.PClient, message string) {
	wsPushca.SendMessageWithAcknowledge4(id, dest, false, message)
}

func (wsPushca *PushcaWebSocket) SendAcknowledge2(binaryID uuid.UUID, order int32) {
	wsPushca.SendAcknowledge(util.BuildAcknowledgeId(binaryID.String(), order))
}
func (wsPushca *PushcaWebSocket) SendAcknowledge(id string) {
	metaData := make(map[string]interface{})
	metaData["messageId"] = id

	_, errWs := wsPushca.wsConnectionWriteShortCommand(Acknowledge, metaData)
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

	_, errWs := wsPushca.wsConnectionWriteCommand(SendMessage, metaData,
		true, "", nil)
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
		if withAcknowledge {
			wsPushca.SendAcknowledge(binaryID.String())
		}
		return
	}
	//-----------------------------------------------------------------------------
	binaryData := wsPushca.Binaries[binaryID]
	if binaryData == nil {
		if wsPushca.UnknownDatagramConsumer != nil {
			wsPushca.UnknownDatagramConsumer(wsPushca, model.UnknownDatagram{
				BinaryId: binaryID,
				Prefix:   inBinary[0:25],
				Order:    order,
				Data:     inBinary[25:],
			})
			return
		}
		log.Printf("Unknown binary with id = %s", binaryID)
		return
	}
	datagram, exists := binaryData.FillWithReceivedData(order, inBinary[25:], &wsPushca.mutex)
	if !exists {
		log.Printf("Unknown datagram: binaryId=%v, order=%d", binaryID, order)
	}
	if int(datagram.Size) != len(datagram.Data) {
		log.Printf("Size validation was not passed: binaryId=%v, order=%d", binaryID, order)
	}
	if datagram.MD5 != util.CalculateSHA256(datagram.Data) {
		log.Printf("MD5 validation was not passed: binaryId=%v, order=%d", binaryID, order)
	}
	if withAcknowledge {
		wsPushca.SendAcknowledge2(binaryID, order)
	}
	if binaryData.IsCompleted(&wsPushca.mutex) {
		if wsPushca.DataConsumer != nil {
			wsPushca.DataConsumer(wsPushca, binaryData.ToBinary(&wsPushca.mutex))
		}
		delete(wsPushca.Binaries, binaryID)
		log.Printf("Binary was successfully received: id=%v, name=%s", binaryID, binaryData.Name)
	}
}

func (wsPushca *PushcaWebSocket) processMessage(inMessage string) {
	if strings.TrimSpace(inMessage) == "" {
		return
	}
	message := inMessage
	if strings.Contains(inMessage, MessagePartsDelimiter) {
		parts := strings.Split(inMessage, MessagePartsDelimiter)

		switch parts[1] {
		case model.Acknowledge.String():
			if callback, ok := wsPushca.AcknowledgeCallbacks.Load(parts[0]); ok {
				if tmp, ok := callback.(*model.AcknowledgeCallback); ok {
					tmp.Received <- DefaultResponse
				}
			}
			return
		case model.BinaryManifest.String():
			wsPushca.processBinaryManifest(parts[2])
			wsPushca.SendAcknowledge(parts[0])
			return
		case model.Response.String():
			var response string
			if len(parts) == 3 {
				response = parts[2]
			} else {
				response = DefaultResponse
			}
			if callback, ok := wsPushca.AcknowledgeCallbacks.Load(parts[0]); ok {
				if tmp, ok := callback.(*model.AcknowledgeCallback); ok {

					tmp.Received <- response
				}
			}
			return
		default:
			wsPushca.SendAcknowledge(parts[0])
			message = parts[1]
		}
	}
	if wsPushca.MessageConsumer != nil {
		wsPushca.MessageConsumer(wsPushca, message)
	}
}

func (wsPushca *PushcaWebSocket) processBinaryManifest(manifestJSON string) {
	var binaryObjectData model.BinaryObjectData
	errUnmarshal := json.Unmarshal([]byte(manifestJSON), &binaryObjectData)
	if errUnmarshal != nil {
		log.Printf("Broken binary binaryObjectData: client %s, error %s", wsPushca.GetInfo(), errUnmarshal)
		return
	}
	if !binaryObjectData.ReadOnly {
		binaryId, _ := uuid.Parse(binaryObjectData.ID)
		wsPushca.Binaries[binaryId] = &binaryObjectData
	}
	if wsPushca.BinaryManifestConsumer != nil {
		wsPushca.BinaryManifestConsumer(wsPushca, binaryObjectData)
	}
}

func (wsPushca *PushcaWebSocket) removeExpiredManifests() {
	toRemove := make([]uuid.UUID, 0)
	now := time.Now().UnixMilli()

	// Identify keys eligible for removal
	for key, value := range wsPushca.Binaries {
		if now-value.Created > (30 * time.Minute).Milliseconds() { // Remove entries created over 30 minutes ago
			toRemove = append(toRemove, key)
		}
	}

	// Remove identified keys
	for _, key := range toRemove {
		delete(wsPushca.Binaries, key)
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

	if withAcknowledge {
		wsPushca.executeWithRepeatOnFailure(
			id.String(),
			func() error {
				return wsPushca.wsConnectionWriteBinary(append(prefix, message...))
			},
			func(err error) {
				log.Printf("Cannot send bimary message: client %s, error %s", wsPushca.GetInfo(), err)
			},
		)
	} else {
		errWs := wsPushca.wsConnectionWriteBinary(append(prefix, message...))
		if errWs != nil {
			log.Printf("Cannot send bimary message: client %s, error %s", wsPushca.GetInfo(), errWs)
		}
	}
}

func (wsPushca *PushcaWebSocket) SendBinaryMessage2(dest model.PClient, message []byte) {
	wsPushca.SendBinaryMessage4(dest, message, uuid.Nil, false)
}

func (wsPushca *PushcaWebSocket) SendBinary7(dest model.PClient, data []byte, name string, pId uuid.UUID, chunkSize int,
	withAcknowledge bool, manifestOnly bool) model.BinaryObjectData {
	id := pId
	if id == uuid.Nil {
		id = uuid.New()
	}

	binaryObjectData := model.ToBinaryObjectData(dest, id, name, wsPushca.Client,
		util.SplitToChunks(data, chunkSize), wsPushca.PusherId, withAcknowledge)
	binaryObjectData.ReadOnly = manifestOnly
	if withAcknowledge {
		wsPushca.SendMessageWithAcknowledge3("", dest, binaryObjectData.BuildBinaryManifest())
	} else {
		wsPushca.SendMessage2(dest, binaryObjectData.BuildBinaryManifest())
	}

	if manifestOnly {
		return binaryObjectData
	}
	wsPushca.SendBinary(binaryObjectData, withAcknowledge, nil)
	return binaryObjectData
}

func (wsPushca *PushcaWebSocket) SendBinary(binaryObjectData model.BinaryObjectData,
	withAcknowledge bool, requestedIds []string) {
	var datagrams []model.Datagram

	filter := func(dgm model.Datagram) bool {
		if requestedIds == nil {
			return true
		}
		ackID := util.BuildAcknowledgeId(binaryObjectData.ID, dgm.Order)
		for _, reqID := range requestedIds {
			if reqID == ackID {
				return true
			}
		}
		return false
	}
	for _, dgm := range binaryObjectData.Datagrams {
		if filter(dgm) {
			datagrams = append(datagrams, dgm)
		}
	}
	for _, d := range datagrams {
		if withAcknowledge {
			wsPushca.executeWithRepeatOnFailure(
				util.BuildAcknowledgeId(binaryObjectData.ID, d.Order),
				func() error {
					return wsPushca.wsConnectionWriteBinary(d.Data)
				},
				func(err error) {
					log.Printf("Cannot send bimary data: client %s, error %s", wsPushca.GetInfo(), err)
				},
			)
		} else {
			errWs := wsPushca.wsConnectionWriteBinary(d.Data)
			if errWs != nil {
				log.Printf("Cannot send bimary data: client %s, error %s", wsPushca.GetInfo(), errWs)
			}
		}
	}
}

func (wsPushca *PushcaWebSocket) SendBinary3(dest model.PClient, data []byte, withAcknowledge bool) {
	wsPushca.SendBinary7(dest, data, "", uuid.Nil, util.DefaultChunkSize, withAcknowledge, false)
}

func (wsPushca *PushcaWebSocket) SendBinary2(dest model.PClient, data []byte) {
	wsPushca.SendBinary3(dest, data, false)
}

func (wsPushca *PushcaWebSocket) registerAcknowledgeCallback(id string) *model.AcknowledgeCallback {
	ackCallback := &model.AcknowledgeCallback{
		Received: make(chan string),
		Done:     wsPushca.done,
	}
	wsPushca.AcknowledgeCallbacks.Store(id, ackCallback)
	return ackCallback
}

func (wsPushca *PushcaWebSocket) WaitForAcknowledge(id string) (string, error) {
	ackCallback := wsPushca.registerAcknowledgeCallback(id)
	select {
	case <-ackCallback.Done:
		return "", errors.New("no result was received")
	case result := <-ackCallback.Received:
		return result, nil
	case <-time.After(AcknowledgeTimeout):
		return "", errors.New("callback timed out")
	}
}

func (wsPushca *PushcaWebSocket) executeWithRepeatOnFailure(id string, operation func() error,
	logError func(err error)) string {
	for i := 0; i < MaxRepeatAttemptNumber; i++ {
		err := operation()
		if err == nil {
			response, err := wsPushca.WaitForAcknowledge(id)
			if err == nil {
				return response
			}
		} else {
			if logError != nil {
				logError(err)
			} else {
				log.Printf("Failed execute operation attempt: id %s, error %v", id, err)
			}
		}
	}
	if logError != nil {
		logError(errors.New("failed to complete"))
	} else {
		log.Printf("Impossible to complete operation: id %s", id)
	}
	return ""
}

func (wsPushca *PushcaWebSocket) wsConnectionWriteShortCommand(command Command,
	metadata map[string]interface{}) (string, error) {
	return wsPushca.wsConnectionWriteCommand(command, metadata, false, "", nil)
}

func (wsPushca *PushcaWebSocket) wsConnectionWriteCommand(command Command,
	metadata map[string]interface{}, waitForCallback bool, callbackId string,
	inLogError func(err error)) (string, error) {
	wsPushca.writeToSocketMutex.Lock()
	defer wsPushca.writeToSocketMutex.Unlock()
	id := callbackId
	if len(id) == 0 {
		id = uuid.New().String()
	}
	commandStr, err := PrepareCommand(command, metadata, id)
	if err != nil {
		return "", err
	}
	var logError func(err error)
	if inLogError == nil {
		logError = func(err error) {
			log.Printf("Cannot send command %s: id %s, client %s, error %s",
				command.String(), id, wsPushca.GetInfo(), err)
		}
	} else {
		logError = inLogError
	}
	if waitForCallback {
		response := wsPushca.executeWithRepeatOnFailure(
			id,
			func() error {
				return wsPushca.webSocket.WriteMessage(commandStr)
			},
			logError,
		)
		return response, nil
	} else {
		err := wsPushca.webSocket.WriteMessage(commandStr)
		if err == nil {
			return DefaultResponse, nil
		} else {
			return "", err
		}
	}
}

func (wsPushca *PushcaWebSocket) wsConnectionWriteMessage(msg string) error {
	wsPushca.writeToSocketMutex.Lock()
	defer wsPushca.writeToSocketMutex.Unlock()
	return wsPushca.webSocket.WriteMessage(msg)
}

func (wsPushca *PushcaWebSocket) wsConnectionWriteJSON(v interface{}) error {
	wsPushca.writeToSocketMutex.Lock()
	defer wsPushca.writeToSocketMutex.Unlock()
	return wsPushca.webSocket.WriteJSON(v)
}

func (wsPushca *PushcaWebSocket) wsConnectionWriteBinary(data []byte) error {
	wsPushca.writeToSocketMutex.Lock()
	defer wsPushca.writeToSocketMutex.Unlock()
	return wsPushca.webSocket.WriteBinary(data)
}
