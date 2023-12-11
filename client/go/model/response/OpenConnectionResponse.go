package response

import "log"

type OpenConnectionResponse struct {
	PusherInstanceId      string `json:"pusherInstanceId"`
	ExternalAdvertisedUrl string `json:"externalAdvertisedUrl"`
	InternalAdvertisedUrl string `json:"internalAdvertisedUrl"`
	BrowserAdvertisedUrl  string `json:"browserAdvertisedUrl"`
}

func (response *OpenConnectionResponse) LogAsString() {
	log.Printf("ExternalAdvertisedUrl: %v,\n InternalAdvertisedUrl: %v,\n BrowserAdvertisedUrl: %v,\nPusherInstanceId: %v",
		response.ExternalAdvertisedUrl,
		response.InternalAdvertisedUrl,
		response.BrowserAdvertisedUrl,
		response.PusherInstanceId,
	)
}
