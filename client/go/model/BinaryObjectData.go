package model

import (
	"sort"
	"sync"
)

var mutex sync.Mutex

type BinaryObjectData struct {
	ID               string     `json:"id"`
	Name             string     `json:"name"`
	Datagrams        []Datagram `json:"datagrams"`
	Sender           PClient    `json:"sender"`
	PusherInstanceId string     `json:"pusherInstanceId"`
}

func (binaryObjectData *BinaryObjectData) FillWithReceivedData(order int32, data []byte) {
	mutex.Lock()
	defer mutex.Unlock()

	datagram := binaryObjectData.getDatagram(order)
	if datagram != nil {
		datagram.Data = data
	}
}

func (binaryObjectData *BinaryObjectData) GetDatagram(order int32) *Datagram {
	mutex.Lock()
	defer mutex.Unlock()

	return binaryObjectData.getDatagram(order)
}

func (binaryObjectData *BinaryObjectData) getDatagram(order int32) *Datagram {
	var result *Datagram
	for _, d := range binaryObjectData.Datagrams {
		if d.Order == order {
			result = &d
			break
		}
	}
	return result
}

func (binaryObjectData *BinaryObjectData) IsCompleted() bool {
	mutex.Lock()
	defer mutex.Unlock()

	for _, d := range binaryObjectData.Datagrams {
		if d.Data == nil {
			return false
		}
	}
	return true
}

func (binaryObjectData *BinaryObjectData) GetDatagrams() []Datagram {
	mutex.Lock()
	defer mutex.Unlock()

	return binaryObjectData.Datagrams
}

func ToBinary(binaryData *BinaryObjectData) Binary {
	dataMap := make(map[int][]byte)
	var orders []int
	for _, d := range binaryData.GetDatagrams() {
		if d.Data != nil {
			dataMap[int(d.Order)] = d.Data
			orders = append(orders, int(d.Order))
		}
	}
	sort.Ints(orders)
	var data []byte
	for _, n := range orders {
		data = append(data, dataMap[n]...)
	}
	return Binary{
		ID:               binaryData.ID,
		Name:             binaryData.Name,
		Sender:           binaryData.Sender,
		PusherInstanceId: binaryData.PusherInstanceId,
		Data:             data,
	}
}
