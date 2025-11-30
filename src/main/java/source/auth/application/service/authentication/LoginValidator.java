package source.auth.application.service.authentication;

import source.auth.AuthExceptions;
import source.auth.application.infra.persistance.jpa.UserRepository;
import source.auth.application.service.authentication.contracts.LoginVerifier;
import source.auth.application.service.cripto.contracts.Hasher;
import source.auth.application.service.device.UserDeviceService;
import source.auth.application.service.validation.ip_handler.contracts.IP;
import source.auth.dto.contracts.UserDTOContract;
import source.auth.model.contracts.UserDB;
import source.auth.model.entity.UserDataBase;
import source.auth.model.entity.UserDevice;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service responsible for authenticating users.
 * It checks if the username exists and if the passphrase is valid.
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
                          UserDeviceService deviceService
    ) {
        this.repository = repository;
        this.hasher = hasher;
        this.ip = ip;
        this.deviceService = deviceService;
    }


    public UserDataBase matcher(UserDTOContract dto, HttpServletRequest request) {

        String username = dto.getUsername();
        String passphrase = hasher.hash(dto.getPassphrase());
        Optional<UserDataBase> user = repository.findByUsername(username);

        if (user.isPresent()) {
            UserDataBase person = user.get();

            if (!person.getPassphrase().equals(passphrase))
                throw new AuthExceptions.InvalidCredentials("Passphrase or username incorrect");

            long clientId = person.getId();

            String requestIp = ip.getIP(request);
            UserDevice device = deviceService.find(clientId).get();
            String clientIp = device.getIpAddress();
            String clientDeviceHash = device.getDeviceHash();
            String requestDeviceHash = ip.getDeviceHash(request);

            if (!clientDeviceHash.equals(requestDeviceHash))
                throw new AuthExceptions.UnrrecognizedDevice("Your device is not recognized");


            return person;


        }

        throw new AuthExceptions.InvalidCredentials("Invalid username or passphrase");


    }


}