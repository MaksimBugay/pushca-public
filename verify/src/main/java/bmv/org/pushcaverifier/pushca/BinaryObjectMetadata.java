package bmv.org.pushcaverifier.pushca;

import bmv.org.pushcaverifier.client.PClient;
import java.util.List;

public record BinaryObjectMetadata(String id, String name, List<Datagram> datagrams, PClient sender,
                                   String pusherInstanceId) {

}
