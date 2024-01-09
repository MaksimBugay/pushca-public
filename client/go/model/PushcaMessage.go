package model

type PMessageType int

const (
	Acknowledge PMessageType = iota
	BinaryManifest
	ChannelMessage
	ChannelEvent
	Response
)

func (messageType PMessageType) String() string {
	types := [...]string{
		"ACKNOWLEDGE",
		"BINARY_MANIFEST",
		"CHANNEL_MESSAGE",
		"CHANNEL_EVENT",
		"RESPONSE",
	}

	if int(messageType) >= len(types) {
		return "Unknown type of Pushca message"
	}

	return types[messageType]
}
