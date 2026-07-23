package source.auth.application.orchestrator.login.contracts;

import source.auth.dto.UserDTO;

public interface Signup {

    source.auth.dto.SignupResponseDTO signupUser(UserDTO dto);

    String createUser(UserDTO dto);
}
