package source.auth.application.service.authentication;

import source.auth.AuthConstants;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistance.jpa.UserRepository;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.device.UserDeviceService;
import source.auth.application.service.validation.ip_handler.contracts.IP;
import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;
import source.auth.model.entity.UserDevice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service responsible for authenticating users during login.
 * Validates credentials and device information.
 */
@Service
public class LoginValidator implements LoginVerifier {

    private final UserRepository repository;
    private final Hasher hasher;
    private final IP ip;
    private final UserDeviceService deviceService;

    public LoginValidator(UserRepository repository,
                          @Qualifier("SHAHasher") Hasher hasher,
                          @Qualifier("IPValidator") IP ip,
                          UserDeviceService deviceService) {
        this.repository = repository;
        this.hasher = hasher;
        this.ip = ip;
        this.deviceService = deviceService;
    }

    /**
     * Matches user credentials and validates device information.
     *
     * @param dto the user credentials
     * @param request the HTTP request containing device information
     * @return the authenticated user entity
     * @throws AuthExceptions.InvalidCredentials if credentials are invalid
     * @throws AuthExceptions.UnrecognizedDeviceException if device is not recognized
     */
    @Override
    public UserDataBase matcher(UserDTOContract dto, HttpServletRequest request) {
        UserDataBase user = matcherWithoutDevice(dto);
        validateDevice(user.getId(), request);
        return user;
    }

    /**
     * Matches user credentials without validating device information.
     *
     * @param dto the user credentials
     * @return the authenticated user entity
     */
    @Override
    public UserDataBase matcherWithoutDevice(UserDTOContract dto) {
        String username = dto.getUsername();
        String hashedPassphrase = hasher.hash(dto.getPassphrase());

        UserDataBase user = repository.findByUsername(username);

        if (user == null) {
            throw new AuthExceptions.InvalidCredentials(AuthConstants.ERR_INVALID_CREDENTIALS);
        }

        validatePassphrase(user.getPassphrase(), hashedPassphrase);
        return user;
    }

    /**
     * Validates that the provided passphrase matches the stored passphrase.
     *
     * @param storedPassphrase the passphrase stored in the database
     * @param providedPassphrase the hashed passphrase from the request
     * @throws AuthExceptions.InvalidCredentials if passphrases don't match
     */
    private void validatePassphrase(String storedPassphrase, String providedPassphrase) {
        if (!storedPassphrase.equals(providedPassphrase)) {
            throw new AuthExceptions.InvalidCredentials(AuthConstants.ERR_INVALID_CREDENTIALS);
        }
    }

    /**
     * Validates that the device making the request is recognized.
     *
     * @param userId the user ID
     * @param request the HTTP request
     * @throws AuthExceptions.UnrecognizedDeviceException if device is not recognized
     */
    private void validateDevice(Long userId, HttpServletRequest request) {
        Optional<UserDevice> deviceOptional = deviceService.find(userId);

        if (deviceOptional.isEmpty()) {
            throw new AuthExceptions.UnrecognizedDeviceException(AuthConstants.ERR_DEVICE_NOT_RECOGNIZED);
        }

        UserDevice device = deviceOptional.get();
        String requestDeviceHash = ip.getDeviceHash(request);
        String storedDeviceHash = device.getDeviceHash();

        if (!storedDeviceHash.equals(requestDeviceHash)) {
            throw new AuthExceptions.UnrecognizedDeviceException(AuthConstants.ERR_DEVICE_NOT_RECOGNIZED);
        }
    }
}