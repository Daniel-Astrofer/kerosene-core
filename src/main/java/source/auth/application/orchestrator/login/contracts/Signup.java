package source.auth.application.orchestrator.login.contracts;

import source.auth.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;

public interface Signup {


    String signupUser(UserDTO dto);

    String createUser(UserDTO dto,
                      HttpServletRequest request);
}
