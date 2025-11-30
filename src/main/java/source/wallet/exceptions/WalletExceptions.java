package source.wallet.exceptions;


public class WalletExceptions {

    public static class WalletExceptionsCreation extends RuntimeException{
        public WalletExceptionsCreation(String message){
            super(message);
        }
    }

    public static class CreateWalletException extends WalletExceptionsCreation {
        public CreateWalletException(String message) {
            super(message);
        }
    }
 }



