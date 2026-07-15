package source.common.exception;

public class FinancialSelfPaymentException extends RuntimeException {

    public FinancialSelfPaymentException() {
        super("You cannot pay or send funds to yourself.");
    }
}
