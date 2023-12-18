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

func (binaryObjectData *BinaryObjectData) FillWithReceivedData(order int32, data []byte) (Datagram, bool) {
	mutex.Lock()
	defer mutex.Unlock()

	datagramIndex := binaryObjectData.getDatagramIndex(order)
	if datagramIndex == -1 {
		return Datagram{}, false
	}
	binaryObjectData.Datagrams[datagramIndex].Data = data
	return binaryObjectData.Datagrams[datagramIndex], true
}

func (binaryObjectData *BinaryObjectData) getDatagramIndex(order int32) int {
	index := -1
	for _, d := range binaryObjectData.Datagrams {
		index += 1
		if d.Order == order {
			return index
		}
	}
	return index
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

func ToBinary(binaryData *BinaryObjectData) Binary {
	mutex.Lock()
	defer mutex.Unlock()

	dataMap := make(map[int][]byte)
	var orders []int
	for _, d := range binaryData.Datagrams {
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
