package bmv.pushca.binary.proxy.pushca.model;

public record WsGatewayRateLimitCheckData(PClient host, String path, GatewayRequestor requestor, String pushcaClusterSecret) {
}
