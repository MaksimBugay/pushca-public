package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"github.com/google/uuid"
	"io"
	"log"
	"net/http"
	"pushca-client/model"
	modelrequest "pushca-client/model/request"
	modelresponse "pushca-client/model/response"
)

func main() {
	deviceId, errRandomUuid := uuid.NewRandom()
	if errRandomUuid != nil {
		log.Fatalf("cannot generate device Id due to %s", errRandomUuid)
		return
	}

	httpPostUrl := "https://app-rc.multiloginapp.net/pushca/open-connection"
	//httpPostUrl := "http://push-app-rc.multiloginapp.net:8050/open-connection"

	openConnectionRequest := &modelrequest.OpenConnectionRequest{
		Client: model.PClient{
			WorkSpaceId:   "workSpaceMain",
			AccountId:     "client0@test.ee",
			DeviceId:      deviceId.String(),
			ApplicationId: "PUSHCA_CLIENT",
		},
	}
	jsonData, errMarshal := json.Marshal(openConnectionRequest)
	if errMarshal != nil {
		log.Fatalf("Unable to marshal open connection request due to %s", errMarshal)
		return
	}

	request, errHttp := http.NewRequest("POST", httpPostUrl, bytes.NewBuffer(jsonData))
	request.Header.Set("Content-Type", "application/json; charset=UTF-8")
	request.Header.Set("User-Agent", "Mozilla")
	request.Header.Set("Accept", "application/json")

	httpClient := &http.Client{}
	response, errHttp := httpClient.Do(request)
	if errHttp != nil {
		log.Fatalf("Unable to send http post due to %s", errHttp)
		return
	}

	fmt.Println("response Status:", response.Status)
	//fmt.Println("response Headers:", response.Header)
	body, _ := io.ReadAll(response.Body)

	var ocResponse modelresponse.OpenConnectionResponse
	errUnmarshal := json.Unmarshal(body, &ocResponse)
	if errUnmarshal != nil {
		log.Fatalf("Unable to marshal JSON due to %s", errUnmarshal)
		return
	}

	fmt.Printf("ExternalAdvertisedUrl: %v\n", ocResponse.ExternalAdvertisedUrl)
	fmt.Printf("InternalAdvertisedUrl: %v\n", ocResponse.InternalAdvertisedUrl)
	fmt.Printf("BrowserAdvertisedUrl: %v\n", ocResponse.BrowserAdvertisedUrl)
	fmt.Printf("PusherInstanceId: %v\n", ocResponse.PusherInstanceId)
}
