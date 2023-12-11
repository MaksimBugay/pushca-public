package request

import "pushca-client/model"

type OpenConnectionRequest struct {
	Client           model.PClient `json:"client"`
	PusherInstanceId string        `json:"pusherInstanceId"`
}
