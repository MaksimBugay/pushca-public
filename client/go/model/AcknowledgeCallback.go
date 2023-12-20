package model

type AcknowledgeCallback struct {
	Received chan bool
	Done     chan struct{}
}
