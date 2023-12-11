package model

type PClient struct {
	WorkSpaceId   string `json:"workSpaceId"`
	AccountId     string `json:"accountId"`
	DeviceId      string `json:"deviceId"`
	ApplicationId string `json:"applicationId"`
}
