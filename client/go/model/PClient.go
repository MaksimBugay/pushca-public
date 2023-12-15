package model

import (
	"fmt"
	"pushca-client/util"
)

type PClient struct {
	WorkSpaceId   string `json:"workSpaceId"`
	AccountId     string `json:"accountId"`
	DeviceId      string `json:"deviceId"`
	ApplicationId string `json:"applicationId"`
}

func (client *PClient) HashCode() int32 {
	return util.CalculateStringHashCode(
		fmt.Sprintf("%s@@%s@@%s@@%s", client.WorkSpaceId, client.AccountId,
			client.DeviceId, client.ApplicationId),
	)
}
