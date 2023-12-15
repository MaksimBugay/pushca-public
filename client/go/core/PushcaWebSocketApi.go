package core

import (
	"github.com/google/uuid"
	"pushca-client/model"
)

type (
	WebSocketApi interface {
		GetFullInfo() string
		GetInfo() string
		OpenConnection(done chan struct{}) error
		CloseConnection() error
		processMessage(inMessage string)
		PingServer()
		SendAcknowledge(id string)
		SendMessageWithAcknowledge4(id string, dest model.PClient, preserveOrder bool, message string)
		SendMessageWithAcknowledge3(id string, dest model.PClient, message string)
		BroadcastMessage4(id string, dest model.ClientFilter, preserveOrder bool, message string)
		BroadcastMessage2(dest model.ClientFilter, message string)
		SendMessage4(id string, dest model.PClient, preserveOrder bool, message string)
		SendMessage2(dest model.PClient, message string)
		SendBinaryMessage4(dest model.PClient, message []byte, id uuid.UUID, withAcknowledge bool)
		SendBinaryMessage2(dest model.PClient, message []byte)
	}
)
