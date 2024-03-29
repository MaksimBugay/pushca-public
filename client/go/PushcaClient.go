package main

import (
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"flag"
	"fmt"
	"github.com/google/uuid"
	"log"
	"os"
	"os/signal"
	"pushca-client/core"
	"pushca-client/model"
	"pushca-client/util"
	"sync"
	"time"
)

func configureTLS() (*tls.Config, error) {
	certFile := "C:\\mbugai\\work\\certificates\\push-app-rc.multiloginapp.net\\pushca-rc.crt" // Path to your certificate file (.pem)
	keyFile := "C:\\mbugai\\work\\certificates\\push-app-rc.multiloginapp.net\\pushca-rc.key"  // Path to your private key file (.pem)

	cert, err := tls.LoadX509KeyPair(certFile, keyFile)
	if err != nil {
		return nil, err
	}

	tlsConfig := &tls.Config{
		Certificates:       []tls.Certificate{cert},
		InsecureSkipVerify: true,
	}

	return tlsConfig, nil
}

func main() {
	tlsConfig, err := configureTLS()
	if err != nil {
		log.Fatalf("cannot load tls config due to %s", err)
	}
	tlsConfig = nil
	done := make(chan struct{})
	deviceId, errRandomUuid := uuid.NewRandom()
	if errRandomUuid != nil {
		log.Fatalf("cannot generate device Id due to %s", errRandomUuid)
	}
	uid, errConversion := util.BytesToUUID(util.UuidToBytes(deviceId))
	if errConversion != nil {
		log.Fatalf("cannot convert to uuid from byte[]")
	}
	if uid != deviceId {
		log.Fatalf("cannot do uuid to byte[] conversion %v", uid)
	}

	messageConsumer := func(ws core.PushcaWebSocketApi, message string) {
		log.Printf("%s message: %s", ws.GetInfo(), message)
	}

	binaryManifestConsumer := func(ws core.PushcaWebSocketApi, manifest model.BinaryObjectData) {
		jsonStr, _ := json.Marshal(manifest)
		log.Printf("%s binary manifest: %s", ws.GetInfo(), jsonStr)
	}

	binaryMessageConsumer := func(ws core.PushcaWebSocketApi, binary []byte) {
		decodedBytes, err := base64.StdEncoding.DecodeString(string(binary))
		if err != nil {
			log.Printf("%s Error attempt of decoding base64 string", ws.GetInfo())
			return
		}
		log.Printf("%s binary message: %s", ws.GetInfo(), string(decodedBytes))
	}

	dataConsumer := func(ws core.PushcaWebSocketApi, binary model.Binary) {
		if binary.ID == "" {
			log.Fatalf("Binary ID is empty")
		}
		// Create a file
		file, err := os.Create(binary.Name)
		if err != nil {
			log.Fatalf("Cannot create file: error %v", err)
		}
		defer func(file *os.File) {
			err := file.Close()
			if err != nil {
				fmt.Printf("cannot close file: error %v\n", err)
			}
		}(file)

		// Write binary data to the file
		_, err = file.Write(binary.Data)
		if err != nil {
			log.Fatalf("Cannot fullfil file content: error %v", err)
		}

		fmt.Println("Binary data was received and stored")
	}

	javaClient := model.PClient{
		WorkSpaceId:   "workSpaceMain",
		AccountId:     "clientJava0@test.ee",
		DeviceId:      "jmeter",
		ApplicationId: "PUSHCA_CLIENT",
	}

	testExclude := make([]model.PClient, 0)
	testExclude = append(testExclude, javaClient)
	testFilter := model.ClientFilter{
		WorkSpaceID:   "workSpaceMain",
		ApplicationID: "PUSHCA_CLIENT",
		Exclude:       testExclude,
	}
	if -960592396 != testFilter.HashCode() {
		log.Fatalf("Wrong filter hash code")
	}
	log.Printf("Filter hash code: %v", testFilter.HashCode())

	//httpPostUrl := "https://app-rc.multiloginapp.net/pushca-with-tls-support/open-connection"
	httpPostUrl := "https://app-rc.multiloginapp.net/pushca/open-connection"
	//httpPostUrl := "http://push-app-rc.multiloginapp.net:8050/open-connection"
	//httpPostUrl := "http://localhost:8080/open-connection"
	wsFactory := func() core.WebSocketApi {
		return &core.GorillaWebSocket{Connection: nil}
	}
	pushcaWebSocket0 := &core.PushcaWebSocket{
		PushcaApiUrl: httpPostUrl,
		Client: model.PClient{
			WorkSpaceId:   "workSpaceGo",
			AccountId:     "clientGo0@test.ee",
			DeviceId:      deviceId.String(),
			ApplicationId: "PUSHCA_CLIENT",
		},
		WebSocketFactory:       wsFactory,
		MessageConsumer:        messageConsumer,
		BinaryManifestConsumer: binaryManifestConsumer,
		BinaryMessageConsumer:  binaryMessageConsumer,
		DataConsumer:           dataConsumer,
		TlsConfig:              tlsConfig,
		Binaries:               new(sync.Map),
		AcknowledgeCallbacks:   new(sync.Map),
		FilterRegistry:         new(sync.Map),
	}
	log.Printf("Pusher instance id: %v", pushcaWebSocket0.PusherId)
	log.Printf("Token: %v", pushcaWebSocket0.Token)
	hashCode := util.CalculateStringHashCode("workSpaceMain@@client2@test.ee@@web-browser@@PUSHCA_CLIENT")
	if hashCode != 1097299416 {
		log.Fatalf("cannot generate hash code similar to java %d", hashCode)
	}
	x, errConversion := util.BytesToInt(util.IntToBytes(hashCode))
	if errConversion != nil {
		log.Fatalf("cannot convert to int from byte[]")
	}
	if x != 1097299416 {
		log.Fatalf("cannot do into to byte[] conversion %d", x)
	}
	pushcaWebSocket1 := &core.PushcaWebSocket{
		PushcaApiUrl: httpPostUrl,
		Client: model.PClient{
			WorkSpaceId:   "workSpaceGo",
			AccountId:     "clientGo1@test.ee",
			DeviceId:      "web-browser",
			ApplicationId: "PUSHCA_CLIENT",
		},
		WebSocketFactory:       wsFactory,
		MessageConsumer:        messageConsumer,
		BinaryManifestConsumer: binaryManifestConsumer,
		BinaryMessageConsumer:  binaryMessageConsumer,
		DataConsumer:           dataConsumer,
		TlsConfig:              tlsConfig,
		Binaries:               new(sync.Map),
		AcknowledgeCallbacks:   new(sync.Map),
		FilterRegistry:         new(sync.Map),
	}
	//================================================================================
	flag.Parse()
	log.SetFlags(0)

	interrupt := make(chan os.Signal, 1)
	signal.Notify(interrupt, os.Interrupt)

	errWsOpen := pushcaWebSocket0.Open(done)
	if errWsOpen != nil {
		log.Fatalf("cannot open web socket connection: client %s, error %s",
			errWsOpen, pushcaWebSocket0.GetInfo())
	}
	defer func(ws core.PushcaWebSocketApi) {
		ws.Close()
	}(pushcaWebSocket0)

	errWsOpen = pushcaWebSocket1.Open(done)
	if errWsOpen != nil {
		log.Fatalf("cannot open web socket connection: client %s, error %s",
			pushcaWebSocket1.GetInfo(), errWsOpen)
	}
	defer func(ws core.PushcaWebSocketApi) {
		ws.Close()
	}(pushcaWebSocket1)

	//time.Sleep(2000 * time.Second)

	newToken := pushcaWebSocket0.RefreshToken()
	log.Printf("New Token: %v", newToken)

	pushcaWebSocket0.SendMessageWithAcknowledge4("1", pushcaWebSocket1.Client, false, "test message with ack")
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

	bMessage := base64.StdEncoding.EncodeToString([]byte("Binary message test"))
	pushcaWebSocket0.SendBinaryMessage4(pushcaWebSocket1.Client, []byte(bMessage), uuid.Nil, true)
	pushcaWebSocket0.SendBinaryMessage2(javaClient, []byte(bMessage))

	vbMessage := base64.StdEncoding.EncodeToString([]byte("Broadcast Binary message test"))
	superBroadFilter := model.ClientFilter{
		ApplicationID: "PUSHCA_CLIENT",
	}
	pushcaWebSocket0.BroadcastBinaryMessage2(superBroadFilter, []byte(vbMessage))
	pushcaWebSocket0.BroadcastBinaryMessage3(superBroadFilter, []byte(vbMessage), deviceId)

	filePath := "C:\\mbugai\\work\\mlx\\pushca-public\\client\\java\\src\\test\\resources\\vlc-3.0.11-win64.exe"
	//filePath := "C:\\mbugai\\work\\mlx\\pushca\\Reproducing_multiple_java_headless.mov"
	data, errFile := util.ReadFileToByteArray(filePath)
	if errFile != nil {
		log.Fatalf("Cannot read data from file: error %s", errFile)
	}
	pushcaWebSocket1.SendBinary7(
		pushcaWebSocket0.Client, data,
		"vlc-3.0.11-win64-copy.exe",
		//"Reproducing_multiple_java_headless-copy.mov",
		uuid.Nil, util.DefaultChunkSize, true)
	defer close(done)
	for {
		select {
		case <-done:
			return
		case <-interrupt:
			log.Println("interrupt")
			select {
			case <-done:
			case <-time.After(5 * time.Second):
			}
			return
		}
	}
}
