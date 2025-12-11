package source.auth.application.service.authentication.contracts;

import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;
import jakarta.servlet.http.HttpServletRequest;

public interface LoginVerifier {

    UserDataBase matcher(UserDTOContract dto, HttpServletRequest request);

    // New: validate credentials (username & passphrase) without enforcing device checks
    UserDataBase matcherWithoutDevice(UserDTOContract dto);
}
