package model

type BinaryObjectMetadata struct {
	ID               string     `json:"id"`
	Name             string     `json:"name"`
	Datagrams        []Datagram `json:"datagrams"`
	Sender           PClient    `json:"sender"`
	PusherInstanceId string     `json:"pusherInstanceId"`
}
