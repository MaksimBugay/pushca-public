package response

type OpenConnectionResponse struct {
	PusherInstanceId      string `json:"pusherInstanceId"`
	ExternalAdvertisedUrl string `json:"externalAdvertisedUrl"`
	InternalAdvertisedUrl string `json:"internalAdvertisedUrl"`
	BrowserAdvertisedUrl  string `json:"browserAdvertisedUrl"`
}
