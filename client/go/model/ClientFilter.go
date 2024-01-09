package model

type ClientFilter struct {
	WorkSpaceID   string    `json:"workSpaceId"`
	AccountID     string    `json:"accountId"`
	DeviceID      string    `json:"deviceId"`
	ApplicationID string    `json:"applicationId"`
	FindAny       bool      `json:"findAny"`
	Exclude       []PClient `json:"exclude"`
}

func FromClient(client PClient) ClientFilter {
	return ClientFilter{
		WorkSpaceID:   client.WorkSpaceId,
		AccountID:     client.AccountId,
		DeviceID:      client.DeviceId,
		ApplicationID: client.ApplicationId,
		FindAny:       false,
		Exclude:       nil,
	}
}

func FromClientWithoutDeviceId(client PClient) ClientFilter {
	return ClientFilter{
		WorkSpaceID:   client.WorkSpaceId,
		AccountID:     client.AccountId,
		ApplicationID: client.ApplicationId,
		FindAny:       false,
		Exclude:       nil,
	}
}
