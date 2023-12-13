package core

import (
	"pushca-client/model"
)

type (
	WebSocketApi interface {
		GetInfo() string
		OpenConnection(done chan struct{}) error
		CloseConnection() error
		SendMessageWithAcknowledge(id string, dest model.PClient, preserveOrder bool, message string)
	}
)
