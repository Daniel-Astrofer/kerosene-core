package com.kerosene.auth.application.orchestrator.signup;

import org.springframework.stereotype.Component;

import com.kerosene.auth.application.orchestrator.login.contracts.Signup;
import com.kerosene.auth.dto.SignupResponseDTO;
import com.kerosene.auth.dto.UserDTO;

@Component
public class SignupUseCase implements Signup {

    private final StartSignup startSignup;
    private final VerifySignupTotp verifySignupTotp;

    public SignupUseCase(StartSignup startSignup,
            VerifySignupTotp verifySignupTotp) {
        this.startSignup = startSignup;
        this.verifySignupTotp = verifySignupTotp;
    }

    @Override
    public SignupResponseDTO signupUser(UserDTO dto) {
        return startSignup.execute(dto);
    }

    @Override
    public String createUser(UserDTO dto) {
        return verifySignupTotp.execute(dto);
    }
}
