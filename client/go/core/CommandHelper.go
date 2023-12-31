package core

import (
	"fmt"
	"pushca-client/util"
)

type Command int

const (
	SendMessage Command = iota
	SendMessageWithAcknowledge
	SendMessageWithPreservedOrder
	SendEnvelopes
	SendMessageToChannel
	SendBinaryManifest
	Ping
	RefreshToken
	Acknowledge
	CreateChannel
	AddMembersToChannel
	MarkChannelAsRead
	GetChannels
	RemoveMeFromChannel
	RegisterFilter
	RemoveFilter
)

func (c Command) String() string {
	commands := [...]string{
		"SEND_MESSAGE",
		"SEND_MESSAGE_WITH_ACKNOWLEDGE",
		"SEND_MESSAGE_WITH_PRESERVED_ORDER",
		"SEND_ENVELOPES",
		"SEND_MESSAGE_TO_CHANNEL",
		"SEND_BINARY_MANIFEST",
		"PING",
		"REFRESH_TOKEN",
		"ACKNOWLEDGE",
		"CREATE_CHANNEL",
		"ADD_MEMBERS_TO_CHANNEL",
		"MARK_CHANNEL_AS_READ",
		"GET_CHANNELS",
		"REMOVE_ME_FROM_CHANNEL",
		"REGISTER_FILTER",
		"REMOVE_FILTER",
	}

	if int(c) >= len(commands) {
		return "Unknown command"
	}

	return commands[c]
}

func PrepareCommand(command Command, metadata map[string]interface{}, callbackId string) (string, error) {
	metaJson, err := util.ToJson(metadata)
	if err != nil {
		return "", err
	}
	var commandStr string
	if metadata == nil {
		commandStr = fmt.Sprintf("%s%s%s", callbackId, MessagePartsDelimiter,
			command)
	} else {
		commandStr = fmt.Sprintf("%s%s%s%s%s", callbackId, MessagePartsDelimiter,
			command, MessagePartsDelimiter,
			metaJson)
	}
	return commandStr, nil
}
