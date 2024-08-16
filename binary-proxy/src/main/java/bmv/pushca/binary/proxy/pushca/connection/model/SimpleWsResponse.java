package bmv.pushca.binary.proxy.pushca.connection.model;

public record SimpleWsResponse(String body, String error) implements WsResponse<String> {

}
