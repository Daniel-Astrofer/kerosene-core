package source.auth.application.service.authentication.contracts;

import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;

public interface LoginVerifier {

    // New: validate credentials (username & passphrase) without enforcing device
    // checks
    UserDataBase matcherWithoutDevice(UserDTOContract dto);
}
