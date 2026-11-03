package bmv.pushca.binary.proxy.pushca.exception;

public class DownloadLimitExceededException extends RuntimeException {
    public static final String CODE = "number-of-downloads-exceeded";

    public DownloadLimitExceededException() {
        super(CODE, null, false, false);
    }
}
