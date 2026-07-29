package com.kerosene.auth.application.service.identityaccess;

import com.kerosene.auth.model.entity.UserDataBase;

public record TransactionalAuthenticationResult(
        UserDataBase user,
        String platformSignature) {

    public TransactionalAuthenticationResult {
        platformSignature = platformSignature != null ? platformSignature : "";
    }
}
