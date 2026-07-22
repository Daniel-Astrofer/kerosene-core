package source.auth.application.service.identityaccess;

public interface TransactionalAuthenticationPort {

    TransactionalAuthenticationResult authorize(TransactionalAuthenticationRequest request);
}
