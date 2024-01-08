package core

import (
	"crypto/tls"
	"github.com/gorilla/websocket"
	"log"
	"strings"
	"time"
)

type GorillaWebSocket struct {
	Connection *websocket.Conn
}

func (wsGorilla *GorillaWebSocket) Open(wsUrl string,
	messageConsumer func(inMessage string),
	dataConsumer func(inBinary []byte),
	onCloseListener func(err error),
	tlsConfig *tls.Config,
	done chan struct{}) error {
	var wsDialer *websocket.Dialer
	if tlsConfig == nil {
		wsDialer = websocket.DefaultDialer
	} else {
		wsDialer = &websocket.Dialer{
			TLSClientConfig: tlsConfig,
		}
	}
	conn, _, errWs := wsDialer.Dial(wsUrl, nil)
	if errWs != nil {
		return errWs
	}
	conn.SetCloseHandler(func(code int, text string) error {
		log.Printf("Connection was closed: code %v, text %s", code, text)
		return nil
	})
	wsGorilla.Connection = conn
	stopSocket := make(chan struct{})
	go func() {
		for {
			select {
			case <-stopSocket:
				return
			case <-done:
				return
			default:
				mType, message, err := conn.ReadMessage()
				if err != nil {
					if isWebSocketWasClosed(err) {
						if onCloseListener != nil {
							onCloseListener(err)
						}
						close(stopSocket)
						wsGorilla.Connection = nil
					} else {
						log.Println("read from socket error:", err)
					}
				} else if mType == websocket.TextMessage {
					messageConsumer(string(message))
				} else if mType == websocket.BinaryMessage {
					dataConsumer(message)
				}
			}
		}
	}()
	return nil
}

func (wsGorilla *GorillaWebSocket) Close() {
	conn := wsGorilla.Connection
	err0 := conn.WriteMessage(
		websocket.CloseMessage,
		websocket.FormatCloseMessage(websocket.CloseNormalClosure, ""))
	if err0 != nil {
		log.Printf("WS cannot be closed normally due to %s", err0)
	}
	time.Sleep(1 * time.Second)
	err := conn.Close()
	if err != nil {
		log.Printf("Ws connection was closed with error: %s", err)
	}
	conn = nil
}

func (wsGorilla *GorillaWebSocket) IsClosed() bool {
	return wsGorilla.Connection == nil
}

func (wsGorilla *GorillaWebSocket) WriteMessage(msg string) error {
	return wsGorilla.Connection.WriteMessage(websocket.TextMessage, []byte(msg))
}

func (wsGorilla *GorillaWebSocket) WriteJSON(v interface{}) error {
	return wsGorilla.Connection.WriteJSON(v)
}

func (wsGorilla *GorillaWebSocket) WriteBinary(data []byte) error {
	return wsGorilla.Connection.WriteMessage(websocket.BinaryMessage, data)
}

func isWebSocketWasClosed(err error) bool {
	if websocket.IsUnexpectedCloseError(err, websocket.CloseNormalClosure) {
		log.Printf("Connection was abnormally closed: %v", err)
	} else if websocket.IsCloseError(err, websocket.CloseNormalClosure) {
		//log.Printf("Connection was normally closed: %v", err)
	} else if strings.Contains(err.Error(), "connection was aborted") {
		log.Printf("Connection was closed because of network issues: %v", err)
	} else {
		return false
	}
	return true
}
