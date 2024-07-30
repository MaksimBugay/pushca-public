package bmv.pushca.binary.proxy.pushca.connection.model;

import java.util.List;

public record OpenConnectionPoolResponse(String pusherInstanceId, List<PusherAddress> addresses) {

}
