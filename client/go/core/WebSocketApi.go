package core

import (
	"github.com/gorilla/websocket"
	"pushca-client/model"
)

type (
	WebSocketApi interface {
		GetInfo() string
		openConnection() (*websocket.Conn, error)
		CloseConnection() error
		SendMessageWithAcknowledge(id string, dest model.PClient, preserveOrder bool, message string)
	}
)
