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

    public static class WalletNameAlreadyExists extends  WalletExceptionsCreation{
        public WalletNameAlreadyExists(String message){
            super(message);
        }
    }
    public static class WalletNoExists extends CreateWalletException {
        public WalletNoExists(String message){
            super(message);
        }

    }
 }



