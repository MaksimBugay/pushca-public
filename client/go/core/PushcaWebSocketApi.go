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
		CloseWebSocket()
		OpenWebSocket() error
		processMessage(inMessage string)
		processBinary(inBinary []byte)
		PingServer()
		RefreshToken()
		SendAcknowledge(id string)
		SendAcknowledge2(binaryID uuid.UUID, order int32)
		SendMessageWithAcknowledge4(id string, dest model.PClient, preserveOrder bool, message string)
		SendMessageWithAcknowledge3(id string, dest model.PClient, message string)
		BroadcastMessage4(id string, dest model.ClientFilter, preserveOrder bool, message string)
		BroadcastMessage2(dest model.ClientFilter, message string)
		SendMessage4(id string, dest model.PClient, preserveOrder bool, message string)
		SendMessage2(dest model.PClient, message string)
		SendBinaryMessage4(dest model.PClient, message []byte, id uuid.UUID, withAcknowledge bool)
		SendBinaryMessage2(dest model.PClient, message []byte)
		SendBinary7(dest model.PClient, data []byte, name string, id uuid.UUID, chunkSize int, withAcknowledge bool, manifestOnly bool) model.BinaryObjectData
		SendBinary3(dest model.PClient, data []byte, withAcknowledge bool)
		SendBinary2(dest model.PClient, data []byte)
		SendBinary(binaryObjectData model.BinaryObjectData, withAcknowledge bool, requestedIds []string)
	}
)
