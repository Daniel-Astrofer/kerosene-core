package source.auth.application.service.authentication;

import source.auth.AuthConstants;
import source.auth.AuthExceptions;
import source.auth.application.infra.persistance.jpa.UserRepository;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.entity.UserDataBase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Service responsible for authenticating users during login.
 * Validates credentials and device information.
 */
@Service
public class LoginValidator implements LoginVerifier {

    private final UserRepository repository;
    private final Hasher hasher;

    public LoginValidator(UserRepository repository,
            @Qualifier("SHAHasher") Hasher hasher) {
        this.repository = repository;
        this.hasher = hasher;
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
        String normalizedPassphrase = dto.getPassphrase().trim().replaceAll("[\\s\\u00A0]+", " ");
        String hashedPassphrase = hasher.hash(normalizedPassphrase);

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
     * @param storedPassphrase   the passphrase stored in the database
     * @param providedPassphrase the hashed passphrase from the request
     * @throws AuthExceptions.InvalidCredentials if passphrases don't match
     */
    private void validatePassphrase(String storedPassphrase, String providedPassphrase) {
        if (!storedPassphrase.equals(providedPassphrase)) {
            throw new AuthExceptions.InvalidCredentials(AuthConstants.ERR_INVALID_CREDENTIALS);
        }
    }

}