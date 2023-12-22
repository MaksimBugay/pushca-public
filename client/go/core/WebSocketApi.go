package core

import "crypto/tls"

type (
	WebSocketApi interface {
		Open(wsUrl string,
			messageConsumer func(inMessage string),
			dataConsumer func(inBinary []byte),
			onCloseListener func(err error),
			tlsConfig *tls.Config,
			done chan struct{}) error

		Close()

		IsClosed() bool

		WriteJSON(v interface{}) error

		WriteBinary(data []byte) error
	}
)
