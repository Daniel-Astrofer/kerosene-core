package source.auth.application.service.authentication.contracts;

import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.contracts.UserDB;
import jakarta.servlet.http.HttpServletRequest;
import source.auth.model.entity.UserDataBase;

public interface LoginVerifier {

    UserDataBase matcher(UserDTOContract dto, HttpServletRequest request);
}
