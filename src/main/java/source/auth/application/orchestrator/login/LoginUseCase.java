package source.auth.application.orchestrator.login;

import source.auth.AuthExceptions;
import source.auth.application.orchestrator.login.contracts.Login;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.application.service.device.UserDeviceService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.jwt.JwtService;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.contracts.UserDB;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import source.auth.model.entity.UserDataBase;
import source.auth.model.entity.UserDevice;
import source.wallet.exceptions.WalletExceptions;

import java.util.Optional;

@Component
public class LoginUseCase implements Login {

    private final LoginVerifier verifier;
    private final JwtServicer service;
    private final UserDeviceService deviceService;
    private final UserServiceContract userService;

    public LoginUseCase(LoginVerifier verifier, JwtServicer service, UserDeviceService deviceService, UserServiceContract userService) {
        this.verifier = verifier;
        this.service = service;
        this.deviceService = deviceService;
        this.userService = userService;
    }


    /*
     * check if the jwt is correct and assigned and give back the id from user on jwt
     * if the jwt is invalid the code check username and passphrase and throws RuntimeException if incorrect*/
    @Override
    public String loginUser(UserDTOContract dto, HttpServletRequest request) {
        UserDataBase user = verifier.matcher(dto, request);

        Long id = user.getId();
        Optional<UserDevice> dbDevice = deviceService.find(id);

        if (request.getHeader("X-Device-Hash").equals(dbDevice.get().getDeviceHash())){
            return id + " " + service.generateToken(id,dbDevice.get().getDeviceHash()) ;
        }

        throw new AuthExceptions.UnrrecognizedDevice("no recognized device");

    }
}
