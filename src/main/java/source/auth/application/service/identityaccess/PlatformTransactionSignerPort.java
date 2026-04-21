package source.auth.application.service.identityaccess;

import source.auth.model.entity.UserDataBase;

public interface PlatformTransactionSignerPort {

    default boolean isAvailable() {
        return true;
    }

    String sign(UserDataBase user);
}
