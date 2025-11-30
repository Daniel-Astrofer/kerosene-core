package source.auth.application.orchestrator.signup;

import source.auth.AuthExceptions;
import source.auth.application.orchestrator.login.contracts.Signup;
import source.auth.application.service.authentication.contracts.SignupVerifier;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.device.UserDeviceService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.application.service.validation.ip_handler.contracts.IP;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.application.service.validation.totp.contratcs.TOTPKeyGenerate;
import source.auth.dto.UserDTO;
import source.auth.model.entity.UserDataBase;
import source.auth.model.entity.UserDevice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;


@Component
public class SignupUseCase implements Signup {

    private final TOTPKeyGenerate totpGenerator;
    private final SignupVerifier verifier;
    private final RedisServicer cache;
    private final UserServiceContract userService;
    private final UserDeviceService deviceService;
    private final IP ip;
    private final JwtServicer jwt;

    public SignupUseCase(TOTPKeyGenerate totpGenerator,
                         SignupVerifier verifier,
                         RedisServicer cache,
                         UserServiceContract userService,
                         UserDeviceService deviceService,
                         IP ip,
                         JwtServicer jwt) {

        this.totpGenerator = totpGenerator;
        this.verifier = verifier;
        this.cache = cache;
        this.userService = userService;
        this.deviceService = deviceService;
        this.ip = ip;
        this.jwt = jwt;
    }

    @Override
    public String signupUser(UserDTO dto) {
        dto.setUsername(dto.getUsername().toLowerCase());
        verifier.verify(dto.getUsername(), dto.getPassphrase());
        String key = totpGenerator.keyGenerator();
        String otpUri = String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s", "Kerosene", dto.getUsername(), key, "Kerosene");
        dto.setTotpSecret(key);
        cache.createTempUser(dto);
        return otpUri;

    }

    @Override
    public String createUser(UserDTO dto,
                             HttpServletRequest request) {
        UserDTO user = cache.getFromRedis(dto);
        if (user == null) {
            throw new AuthExceptions.TotpTimeExceded("account is no more available,signup again");
        }
        String deviceHash = request.getHeader("X-Device-Hash");
        if (deviceHash.isEmpty() || deviceHash.equalsIgnoreCase("unknown")) {
            throw new AuthExceptions.UnrrecognizedDevice("device not recognized");
        }

        UserDataBase userDB = userService.fromDTO(user);
        userService.createUserInDataBase(userDB);
        UserDevice device = new UserDevice();
        device.setUser(userDB);
        device.setDeviceHash(deviceHash);
        device.setIpAddress(ip.getIP(request));
        deviceService.create(device);
        cache.deleteFromRedis(user);

        return jwt.generateToken(userDB.getId(), device.getDeviceHash());

    }
}
