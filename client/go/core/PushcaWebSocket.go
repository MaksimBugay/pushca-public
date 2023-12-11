package core

import (
	"bytes"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"pushca-client/model"
	modelrequest "pushca-client/model/request"
	modelresponse "pushca-client/model/response"
)

type (
	OpenConnectionHelper interface {
		openConnection() (modelresponse.OpenConnectionResponse, error)
	}
)

type PushcaWebSocket struct {
	PushcaApiUrl string
	PusherId     string
	Client       model.PClient
	WsUrl        string
}

func (wsPushca *PushcaWebSocket) openConnection() (modelresponse.OpenConnectionResponse, error) {
	openConnectionRequest := &modelrequest.OpenConnectionRequest{
		Client: wsPushca.Client,
	}
	jsonData, errMarshal := json.Marshal(openConnectionRequest)
	if errMarshal != nil {
		log.Printf("Unable to marshal open connection request due to %s\n", errMarshal)
		return modelresponse.OpenConnectionResponse{}, errMarshal
	}

	request, errHttp := http.NewRequest("POST", wsPushca.PushcaApiUrl, bytes.NewBuffer(jsonData))
	request.Header.Set("Content-Type", "application/json; charset=UTF-8")
	request.Header.Set("User-Agent", "Mozilla")
	request.Header.Set("Accept", "application/json")

	httpClient := &http.Client{}
	httpResponse, errHttp := httpClient.Do(request)
	if errHttp != nil {
		log.Printf("Unable to send http post due to %s", errHttp)
		return modelresponse.OpenConnectionResponse{}, errHttp
	}

	//fmt.Println("response Status:", httpResponse.Status)
	//fmt.Println("response Headers:", response.Header)
	body, _ := io.ReadAll(httpResponse.Body)

	var ocResponse modelresponse.OpenConnectionResponse
	errUnmarshal := json.Unmarshal(body, &ocResponse)
	if errUnmarshal != nil {
		log.Printf("Unable to marshal JSON due to %s", errUnmarshal)
		return modelresponse.OpenConnectionResponse{}, errUnmarshal
	}
	ocResponse.LogAsString()
	wsPushca.PusherId = ocResponse.PusherInstanceId
	wsPushca.WsUrl = ocResponse.ExternalAdvertisedUrl
	return ocResponse, nil
}

func InitWebSocket(helper OpenConnectionHelper) {
	_, err := helper.openConnection()
	if err != nil {
		recover()
	}
}
