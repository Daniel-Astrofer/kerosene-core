package com.kerosene.auth.application.service.identityaccess;

import com.kerosene.auth.model.entity.UserDataBase;

public interface PlatformTransactionSignerPort {

    default boolean isAvailable() {
        return true;
    }

    String sign(UserDataBase user);
}
