package source.kfe.exception;

public class KfeSelfPaymentException extends RuntimeException {

    public KfeSelfPaymentException() {
        super("You cannot pay or send funds to yourself.");
    }
}
