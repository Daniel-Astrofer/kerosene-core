package source.auth.application.service.identityaccess;

import source.auth.model.entity.UserDataBase;

public record TransactionalAuthenticationResult(
        UserDataBase user,
        String platformSignature) {

    public TransactionalAuthenticationResult {
        platformSignature = platformSignature != null ? platformSignature : "";
    }
}
