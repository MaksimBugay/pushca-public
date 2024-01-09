package model

type AcknowledgeCallback struct {
	Received chan string
	Done     chan struct{}
}
