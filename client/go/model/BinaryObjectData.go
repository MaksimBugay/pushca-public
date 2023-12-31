package model

import (
	"github.com/google/uuid"
	"sort"
	"sync"
	"time"
)

type BinaryObjectData struct {
	ID               string     `json:"id"`
	Name             string     `json:"name"`
	Datagrams        []Datagram `json:"datagrams"`
	Sender           PClient    `json:"sender"`
	PusherInstanceId string     `json:"pusherInstanceId"`
	ReadOnly         bool       `json:"readOnly"`
	Created          int64      `json:"created"`
}

func (binaryObjectData *BinaryObjectData) FillWithReceivedData(order int32, data []byte, mutex *sync.Mutex) (Datagram, bool) {
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

func (binaryObjectData *BinaryObjectData) IsCompleted(mutex *sync.Mutex) bool {
	mutex.Lock()
	defer mutex.Unlock()

	for _, d := range binaryObjectData.Datagrams {
		if d.Data == nil {
			return false
		}
	}
	return true
}

func ToBinaryObjectData(dest PClient, id uuid.UUID, name string,
	sender PClient, chunks [][]byte, pusherInstanceId string, withAcknowledge bool) BinaryObjectData {
	var datagrams []Datagram

	for i, chunk := range chunks {
		d := ToDatagram(id, int32(i), chunk, dest, withAcknowledge)
		datagrams = append(datagrams, d)
	}

	return BinaryObjectData{
		ID:               id.String(),
		Name:             name,
		Datagrams:        datagrams,
		Sender:           sender,
		PusherInstanceId: pusherInstanceId,
		ReadOnly:         false,
		Created:          time.Now().UnixMilli(),
	}
}

func (binaryObjectData *BinaryObjectData) ToBinary(mutex *sync.Mutex) Binary {
	mutex.Lock()
	defer mutex.Unlock()

	dataMap := make(map[int][]byte)
	var orders []int
	for _, d := range binaryObjectData.Datagrams {
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
		ID:               binaryObjectData.ID,
		Name:             binaryObjectData.Name,
		Sender:           binaryObjectData.Sender,
		PusherInstanceId: binaryObjectData.PusherInstanceId,
		Data:             data,
	}
}
