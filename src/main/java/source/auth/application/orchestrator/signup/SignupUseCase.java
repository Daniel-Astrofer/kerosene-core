package source.auth.application.orchestrator.signup;

import source.auth.AuthConstants;
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

/**
 * Use case orchestrator for user signup process.
 * Handles TOTP generation, user validation, and account creation.
 */
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

    /**
     * Initiates the signup process by validating user credentials and generating TOTP.
     * 
     * @param dto the user data transfer object containing username and passphrase
     * @return TOTP URI for QR code generation
     * @throws AuthExceptions.AuthValidationException if validation fails
     */
    @Override
    public String signupUser(UserDTO dto) {
        String normalizedUsername = dto.getUsername().toLowerCase();
        dto.setUsername(normalizedUsername);
        
        verifier.verify(dto.getUsername(), dto.getPassphrase());
        
        String totpKey = totpGenerator.keyGenerator();
        String otpUri = String.format(
            AuthConstants.TOTP_URI_FORMAT,
            AuthConstants.APP_NAME,
            dto.getUsername(),
            totpKey,
            AuthConstants.APP_NAME
        );
        
        dto.setTotpSecret(totpKey);
        cache.createTempUser(dto);
        
        return otpUri;
    }

    /**
     * Completes the signup process by verifying TOTP and creating the user account.
     * 
     * @param dto the user data transfer object containing TOTP code
     * @return JWT token for authentication
     * @throws AuthExceptions.TotpTimeExceededException if TOTP verification window expired
     * @throws AuthExceptions.UnrecognizedDeviceException if device hash is invalid
     */
    @Override
    public String createUser(UserDTO dto, String deviceHash,HttpServletRequest request) {
        UserDTO cachedUser = cache.getFromRedis(dto);
        
        if (cachedUser == null) {
            throw new AuthExceptions.TotpTimeExceededException(AuthConstants.ERR_TOTP_EXPIRED);
        }
        validateDeviceHash(deviceHash);
        
        UserDataBase userDB = userService.fromDTO(cachedUser);
        userService.createUserInDataBase(userDB);
        
        UserDevice device = createUserDevice(userDB, deviceHash, ip.getIP(request));
        deviceService.create(device);
        
        cache.deleteFromRedis(cachedUser);
        
        return jwt.generateToken(userDB.getId(), device.getDeviceHash());
    }

    /**
     * Validates the device hash from the request header.
     * 
     * @param deviceHash the device hash to validate
     * @throws AuthExceptions.UnrecognizedDeviceException if device hash is invalid
     */
    private void validateDeviceHash(String deviceHash) {
        if (deviceHash == null || deviceHash.isEmpty() || deviceHash.equalsIgnoreCase("unknown")) {
            throw new AuthExceptions.UnrecognizedDeviceException(AuthConstants.ERR_DEVICE_NOT_RECOGNIZED);
        }
    }

    /**
     * Creates a UserDevice entity with the provided information.
     * 
     * @param user the user database entity
     * @param deviceHash the device hash
     * @return the created UserDevice entity
     */
    private UserDevice createUserDevice(UserDataBase user, String deviceHash, String ip) {
        UserDevice device = new UserDevice();
        device.setUser(user);
        device.setDeviceHash(deviceHash);
        device.setIpAddress(ip);
        return device;
    }
}
