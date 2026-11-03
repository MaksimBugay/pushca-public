package bmv.pushca.binary.proxy.pushca.exception;

public class BinaryNotFoundException extends RuntimeException {
    public static final String CODE = "binary-not-found";

    public BinaryNotFoundException() {
        super(CODE, null, false, false);
    }
}
