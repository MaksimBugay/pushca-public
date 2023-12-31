package bmv.org.pushcaverifier.pushca;

public record Datagram(
    int size,
    String md5,
    String prefix,
    String id,
    int order
) {

}
