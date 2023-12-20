package model

import (
	"github.com/google/uuid"
	"pushca-client/util"
)

type Datagram struct {
	Size   int32  `json:"size"`
	MD5    string `json:"md5"`
	Prefix []byte `json:"prefix"`
	Order  int32  `json:"order"`
	Data   []byte `json:"-"`
}

type UnknownDatagram struct {
	BinaryId uuid.UUID `json:"binaryId"`
	Prefix   []byte    `json:"prefix"`
	Order    int32     `json:"order"`
	Data     []byte    `json:"-"`
}

func ToDatagram(binaryID uuid.UUID, order int32, chunk []byte, dest PClient,
	withAcknowledge bool) Datagram {
	prefix := util.ToDatagramPrefix(binaryID, order, dest.HashCode(), withAcknowledge)
	return Datagram{
		Order:  order,
		Size:   int32(len(chunk)),
		MD5:    util.CalculateSHA256(chunk),
		Prefix: prefix,
		Data:   append(prefix, chunk...),
	}
}
