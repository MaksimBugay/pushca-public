package bmv.org.pushcaverifier.client.rest.response;

import bmv.org.pushcaverifier.client.rest.PusherAddress;
import java.util.List;

public record OpenConnectionPoolResponse(String pusherInstanceId, List<PusherAddress> addresses) {

}
