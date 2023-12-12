package model

type CommandWithMetaData struct {
	Command  string                 `json:"command"`
	MetaData map[string]interface{} `json:"metaData"`
}
