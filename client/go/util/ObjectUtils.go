package util

import (
	"encoding/binary"
	"fmt"
	"github.com/google/uuid"
)

func CalculateStringHashCode(s string) int32 {
	var h int32

	for _, b := range []byte(s) {
		h = 31*h + int32(b&0xff)
	}

	return h
}

func BooleanToBytes(value bool) []byte {
	var result byte
	if value {
		result = 1
	}

	return []byte{result}
}

func BytesToBoolean(bytes []byte) (bool, error) {
	if bytes == nil || len(bytes) != 1 {
		return false, fmt.Errorf("cannot convert byte array to boolean")
	}
	return bytes[0] != 0, nil
}

func UuidToBytes(value uuid.UUID) []byte {
	var bytes [16]byte
	copy(bytes[:8], value[:8])
	copy(bytes[8:], value[8:])
	return bytes[:]
}

func BytesToUUID(bytes []byte) (uuid.UUID, error) {
	if len(bytes) != 16 {
		return uuid.UUID{}, fmt.Errorf("invalid byte length for UUID")
	}
	return uuid.FromBytes(bytes)
}

func IntToBytes(value int32) []byte {
	buf := make([]byte, 4)
	binary.BigEndian.PutUint32(buf, uint32(value))
	return buf
}

func BytesToInt(bytes []byte) (int32, error) {
	if len(bytes) != 4 {
		return 0, fmt.Errorf("invalid byte length for int")
	}
	return int32(binary.BigEndian.Uint32(bytes)), nil
}

func ToDatagramPrefix(id uuid.UUID, order int32, clientHashCode int32, withAcknowledge bool) []byte {
	prefix := append(
		append(
			append(IntToBytes(clientHashCode), BooleanToBytes(withAcknowledge)...),
			UuidToBytes(id)...,
		), IntToBytes(order)...,
	)
	return prefix
}
