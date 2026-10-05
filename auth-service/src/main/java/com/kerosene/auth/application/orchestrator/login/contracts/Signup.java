package com.kerosene.auth.application.orchestrator.login.contracts;

import com.kerosene.auth.dto.UserDTO;

public interface Signup {

    com.kerosene.auth.dto.SignupResponseDTO signupUser(UserDTO dto);

    String createUser(UserDTO dto);
}
