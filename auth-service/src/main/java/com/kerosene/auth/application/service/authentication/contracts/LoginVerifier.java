package com.kerosene.auth.application.service.authentication.contracts;

import com.kerosene.auth.dto.contracts.UserDTOContract;
import com.kerosene.auth.model.entity.UserDataBase;

/** Port for validating primary credentials and resolving a user for second-factor login. */
public interface LoginVerifier {

    /** Validates username and passphrase without enforcing device-specific checks. */
    /** @param dto submitted credentials */
    /** @return authenticated user after successful validation */
    UserDataBase matcherWithoutDevice(UserDTOContract dto);

    /** Looks up an account by username after the initial login stage already checked its passphrase. */
    /** @param username submitted account username */
    /** @return persisted user or the implementation's invalid-credentials outcome */
    UserDataBase findByUsernameOnly(String username);
}
