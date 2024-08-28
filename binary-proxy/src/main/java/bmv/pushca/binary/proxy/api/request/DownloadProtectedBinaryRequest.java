package bmv.pushca.binary.proxy.api.request;

public record DownloadProtectedBinaryRequest(String suffix, long exp, String signature, String binaryId) {

}
