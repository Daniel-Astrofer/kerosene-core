package source.auth.application.service.authentication.contracts;

import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;

public interface LoginVerifier {

    // New: validate credentials (username & passphrase) without enforcing device
    // checks
    UserDataBase matcherWithoutDevice(UserDTOContract dto);

    // Lookup by username only — used by TOTP verify step where passphrase was
    // already validated in the initial login step.
    UserDataBase findByUsernameOnly(String username);
}
