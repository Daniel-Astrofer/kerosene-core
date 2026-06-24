package source.common.exception;

public class FinancialProviderUnavailableException extends KeroseneException {

    public static final String ERROR_CODE = "ERR_KFE_RAIL_PROVIDER_UNAVAILABLE";

    public FinancialProviderUnavailableException(String message) {
        super(message, ERROR_CODE);
    }

    public FinancialProviderUnavailableException(String message, Throwable cause) {
        super(message, cause, ERROR_CODE);
    }
}
