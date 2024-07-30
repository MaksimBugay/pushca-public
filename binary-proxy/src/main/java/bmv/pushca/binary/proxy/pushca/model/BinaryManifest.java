package bmv.pushca.binary.proxy.pushca.model;

import java.util.List;

public record BinaryManifest(String id, String name, String mimeType,
                             List<Datagram> datagrams, PClient sender,
                             String pusherInstanceId) {

}
