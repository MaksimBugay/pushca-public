package bmv.pushca.binary.proxy.pushca;

public record Datagram(
    int size,
    String md5,
    String prefix,
    int order
) {

}
