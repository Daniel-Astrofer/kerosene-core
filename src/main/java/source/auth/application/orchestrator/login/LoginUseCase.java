package source.auth.application.orchestrator.login;

import source.auth.AuthExceptions;
import source.auth.application.orchestrator.login.contracts.Login;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.application.service.device.UserDeviceService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.application.service.validation.totp.contratcs.TOTPVerifier;
import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;
import source.auth.model.entity.UserDevice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class LoginUseCase implements Login {

    private final LoginVerifier verifier;
    private final JwtServicer service;
    private final UserDeviceService deviceService;
    private final UserServiceContract userService;
    private final TOTPVerifier totpVerifier;

    public LoginUseCase(LoginVerifier verifier, JwtServicer service, UserDeviceService deviceService, UserServiceContract userService, TOTPVerifier totpVerifier) {
        this.verifier = verifier;
        this.service = service;
        this.deviceService = deviceService;
        this.userService = userService;
        this.totpVerifier = totpVerifier;
    }


    /*
     * check if the jwt is correct and assigned and give back the id from user on jwt
     * if the jwt is invalid the code check username and passphrase and throws RuntimeException if incorrect*/
    @Override
    public String loginUser(UserDTOContract dto, HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (!auth.getName().equalsIgnoreCase("anonymousUser") ){
            Long id = Long.parseLong(auth.getName());
            Optional<UserDevice>  dbDevice = deviceService.find(id);
            if (dbDevice.isPresent() && service.extractDevice(auth.getCredentials().toString()).equals(dbDevice.get().getDeviceHash())){
                return id + " " + service.generateToken(id,dbDevice.get().getDeviceHash()) ;
            }
        }

        UserDataBase user = verifier.matcher(dto, request);
        Optional<UserDevice> device = deviceService.find(user.getId());
        return user.getId() + " " + service.generateToken(user.getId(),device.get().getDeviceHash()) ;


    }

    @Override
    public String loginTotpVerify(UserDTOContract dto, String deviceHash, HttpServletRequest request) {

        // validate only credentials (username + passphrase) without device enforcement
        UserDataBase user = verifier.matcherWithoutDevice(dto);
        Optional<UserDevice> deviceOpt = deviceService.find(user.getId());


        if (deviceOpt.isPresent() && deviceHash != null && deviceHash.equals(deviceOpt.get().getDeviceHash())) {
            return user.getId() + " " + service.generateToken(user.getId(), deviceOpt.get().getDeviceHash());
        }

        if (dto.getTotpCode() == null || dto.getTotpCode().isEmpty()) {
            throw new AuthExceptions.incorrectTotp("TOTP code required when device is not recognized");
        }


        totpVerifier.totpVerify(user.getTOTPSecret(), dto.getTotpCode());

        String ip = request.getRemoteAddr();
        if (deviceHash != null && !deviceHash.isEmpty() && !deviceHash.equalsIgnoreCase("unknown")) {
            UserDevice newDevice = new UserDevice();
            newDevice.setUser(user);
            newDevice.setDeviceHash(deviceHash);
            newDevice.setIpAddress(ip);

            if (deviceOpt.isPresent()) {
                deviceService.update(deviceOpt.get().getId(), newDevice);
            } else {
                deviceService.create(newDevice);
            }

            return user.getId() + " " + service.generateToken(user.getId(), deviceHash);
        }

        if (deviceOpt.isPresent()) {
            return user.getId() + " " + service.generateToken(user.getId(), deviceOpt.get().getDeviceHash());
        }

        return user.getId() + " " + service.generateToken(user.getId(), "");
    }
}
