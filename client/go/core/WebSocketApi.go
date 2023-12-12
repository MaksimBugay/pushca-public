package core

import "github.com/gorilla/websocket"

type (
	WebSocketApi interface {
		GetInfo() string
		openConnection() (*websocket.Conn, error)
		CloseConnection() error
	}
)
