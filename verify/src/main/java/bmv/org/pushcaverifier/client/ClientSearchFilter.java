package bmv.org.pushcaverifier.client;

public record ClientSearchFilter(String workSpaceId, String accountId, String deviceId,
                                 String applicationId, boolean findAny) {

}
