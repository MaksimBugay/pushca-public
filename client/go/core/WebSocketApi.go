package core

type (
	WebSocketApi interface {
		Open(wsUrl string,
			messageConsumer func(inMessage string),
			dataConsumer func(inBinary []byte),
			onCloseListener func(err error),
			done chan struct{}) error

		Close()

		IsClosed() bool

		WriteJSON(v interface{}) error

		WriteBinary(data []byte) error
	}
)
