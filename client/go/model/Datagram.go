package model

type Datagram struct {
	Size   int32  `json:"size"`
	MD5    string `json:"md5"`
	Prefix []byte `json:"prefix"`
	Order  int32  `json:"order"`
	Data   []byte `json:"-"`
}
