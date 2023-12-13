package model

type ClientFilter struct {
	WorkSpaceID   string    `json:"workSpaceId"`
	AccountID     string    `json:"accountId"`
	DeviceID      string    `json:"deviceId"`
	ApplicationID string    `json:"applicationId"`
	FindAny       bool      `json:"findAny"`
	Exclude       []PClient `json:"exclude"`
}
