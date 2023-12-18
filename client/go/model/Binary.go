package model

type Binary struct {
	ID               string
	Name             string
	Sender           PClient
	PusherInstanceId string
	Data             []byte
}
