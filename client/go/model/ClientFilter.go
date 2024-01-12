package model

import (
	"fmt"
	"pushca-client/util"
	"strconv"
	"strings"
)

type ClientFilter struct {
	WorkSpaceID   string    `json:"workSpaceId"`
	AccountID     string    `json:"accountId"`
	DeviceID      string    `json:"deviceId"`
	ApplicationID string    `json:"applicationId"`
	FindAny       bool      `json:"findAny"`
	Exclude       []PClient `json:"exclude"`
}

func (filter *ClientFilter) HashCode() int32 {
	var sb strings.Builder

	sb.WriteString(filter.WorkSpaceID)
	sb.WriteString("|")
	sb.WriteString(filter.AccountID)
	sb.WriteString("|")
	sb.WriteString(filter.DeviceID)
	sb.WriteString("|")
	sb.WriteString(filter.ApplicationID)
	sb.WriteString("|")
	sb.WriteString(strconv.FormatBool(filter.FindAny))
	sb.WriteString("|")

	if len(filter.Exclude) > 0 {
		var excludeParts []string
		for _, client := range filter.Exclude {
			excludeParts = append(excludeParts, fmt.Sprintf("%v", client.HashCode()))
		}
		sb.WriteString(strings.Join(excludeParts, ","))
	}

	return util.CalculateStringHashCode(sb.String())
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
