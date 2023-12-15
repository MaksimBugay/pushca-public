package model

type Datagram struct {
	Size   int    `json:"size"`
	MD5    string `json:"md5"`
	Prefix []byte `json:"prefix"`
	ID     string `json:"id"`
	Order  int    `json:"order"`
	Data   []byte `json:"-"`
}
