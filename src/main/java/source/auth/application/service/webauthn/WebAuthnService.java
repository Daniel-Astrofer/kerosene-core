package source.auth.application.service.webauthn;

import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import source.auth.application.infra.persistance.jpa.PasskeyCredentialRepository;
import source.auth.application.infra.persistance.jpa.UserRepository;
import source.auth.model.entity.PasskeyCredential;
import source.auth.model.entity.UserDataBase;

import org.springframework.beans.factory.annotation.Value;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WebAuthnService {

    private static final Logger log = LoggerFactory.getLogger(WebAuthnService.class);

    private final RelyingParty relyingParty;
    private final PasskeyCredentialRepository credentialRepository;
    private final UserRepository userRepository;

    public WebAuthnService(
            PasskeyCredentialRepository credentialRepository,
            UserRepository userRepository,
            @Value("${webauthn.rp.id:localhost}") String rpId,
            @Value("${webauthn.rp.name:Kerosene}") String rpName) {
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;

        RelyingPartyIdentity rpIdentity = RelyingPartyIdentity.builder()
                .id(rpId)
                .name(rpName)
                .build();

        this.relyingParty = RelyingParty.builder()
                .identity(rpIdentity)
                .credentialRepository(new KeroseneCredentialRepository())
                .build();
    }

    public RelyingParty getRelyingParty() {
        return relyingParty;
    }

    public com.yubico.webauthn.data.PublicKeyCredentialCreationOptions startRegistration(String username) {
        try {
            var request = com.yubico.webauthn.StartRegistrationOptions.builder()
                    .user(com.yubico.webauthn.data.UserIdentity.builder()
                            .name(username)
                            .displayName(username)
                            .id(new ByteArray(username.getBytes()))
                            .build())
                    .build();

            return relyingParty.startRegistration(request);
        } catch (Exception e) {
            log.error("Failed to generate registration options", e);
            throw new RuntimeException("WebAuthn generation failed: " + e.getMessage());
        }
    }

    public void finishRegistration(String username, String creationOptionsJson, String credentialResponseJson) {
        try {
            var creationOptions = com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
                    .fromJson(creationOptionsJson);
            var pkc = com.yubico.webauthn.data.PublicKeyCredential
                    .parseRegistrationResponseJson(credentialResponseJson);

            var registrationResult = relyingParty
                    .finishRegistration(com.yubico.webauthn.FinishRegistrationOptions.builder()
                            .request(creationOptions)
                            .response(pkc)
                            .build());

            UserDataBase user = userRepository.findByUsername(username);
            if (user == null) {
                throw new source.auth.AuthExceptions.InvalidCredentials("User not found for registration");
            }

            PasskeyCredential cred = new PasskeyCredential();
            cred.setCredentialId(registrationResult.getKeyId().getId().getBytes());
            cred.setUserHandle(creationOptions.getUser().getId().getBytes());
            cred.setPublicKeyCose(registrationResult.getPublicKeyCose().getBytes());
            cred.setSignatureCount(registrationResult.getSignatureCount());
            cred.setUser(user);

            credentialRepository.save(cred);

        } catch (Exception e) {
            log.error("Failed to finish registration", e);
            throw new RuntimeException("WebAuthn registration failed: " + e.getMessage());
        }
    }

    public String finishOnboardingRegistration(String username, String creationOptionsJson,
            String credentialResponseJson) {
        try {
            var creationOptions = com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
                    .fromJson(creationOptionsJson);
            var pkc = com.yubico.webauthn.data.PublicKeyCredential
                    .parseRegistrationResponseJson(credentialResponseJson);

            var registrationResult = relyingParty
                    .finishRegistration(com.yubico.webauthn.FinishRegistrationOptions.builder()
                            .request(creationOptions)
                            .response(pkc)
                            .build());

            PasskeyCredential cred = new PasskeyCredential();
            cred.setCredentialId(registrationResult.getKeyId().getId().getBytes());
            cred.setUserHandle(creationOptions.getUser().getId().getBytes());
            cred.setPublicKeyCose(registrationResult.getPublicKeyCose().getBytes());
            cred.setSignatureCount(registrationResult.getSignatureCount());

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(cred);

        } catch (Exception e) {
            log.error("Failed to finish onboarding registration", e);
            throw new RuntimeException("WebAuthn onboarding registration failed: " + e.getMessage());
        }
    }

    public String startLogin(String username) {
        try {
            var assertionRequest = relyingParty.startAssertion(com.yubico.webauthn.StartAssertionOptions.builder()
                    .username(username)
                    .build());

            return assertionRequest.toCredentialsGetJson();
        } catch (Exception e) {
            log.error("Failed to start passkey login for {}", username, e);
            throw new RuntimeException("WebAuthn login generation failed: " + e.getMessage());
        }
    }

    public boolean finishLogin(String assertionRequestJson, String credentialResponseJson) {
        try {
            var assertionRequest = com.yubico.webauthn.AssertionRequest.fromJson(assertionRequestJson);
            var pkc = com.yubico.webauthn.data.PublicKeyCredential.parseAssertionResponseJson(credentialResponseJson);

            var result = relyingParty.finishAssertion(com.yubico.webauthn.FinishAssertionOptions.builder()
                    .request(assertionRequest)
                    .response(pkc)
                    .build());

            if (result.isSuccess()) {
                // Update signature count
                var credOpt = credentialRepository
                        .findByCredentialId(result.getCredential().getCredentialId().getBytes());
                if (credOpt.isPresent()) {
                    var cred = credOpt.get();
                    cred.setSignatureCount(result.getSignatureCount());
                    credentialRepository.save(cred);
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to finish passkey login", e);
            throw new RuntimeException("WebAuthn login failed: " + e.getMessage());
        }
    }

    /**
     * Inner class implementing com.yubico.webauthn.CredentialRepository
     * Provides WebAuthn library access to our database.
     */
    private class KeroseneCredentialRepository implements CredentialRepository {
        @Override
        public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
            UserDataBase user = userRepository.findByUsername(username);
            if (user == null)
                return Set.of();

            return credentialRepository.findByUserId(user.getId()).stream()
                    .map(c -> PublicKeyCredentialDescriptor.builder().id(new ByteArray(c.getCredentialId())).build())
                    .collect(Collectors.toSet());
        }

        @Override
        public Optional<ByteArray> getUserHandleForUsername(String username) {
            UserDataBase user = userRepository.findByUsername(username);
            if (user == null)
                return Optional.empty();

            var creds = credentialRepository.findByUserId(user.getId());
            if (creds.isEmpty())
                return Optional.empty();

            return Optional.of(new ByteArray(creds.get(0).getUserHandle())); // Return first handle found
        }

        @Override
        public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
            var creds = credentialRepository.findByUserHandle(userHandle.getBytes());
            if (creds.isEmpty())
                return Optional.empty();

            return Optional.of(creds.get(0).getUser().getUsername());
        }

        @Override
        public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
            Optional<PasskeyCredential> credOpt = credentialRepository.findByCredentialId(credentialId.getBytes());
            if (credOpt.isEmpty())
                return Optional.empty();

            PasskeyCredential cred = credOpt.get();
            return Optional.of(
                    RegisteredCredential.builder()
                            .credentialId(new ByteArray(cred.getCredentialId()))
                            .userHandle(new ByteArray(cred.getUserHandle()))
                            .publicKeyCose(new ByteArray(cred.getPublicKeyCose()))
                            .signatureCount(cred.getSignatureCount())
                            .build());
        }

        @Override
        public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
            Optional<PasskeyCredential> credOpt = credentialRepository.findByCredentialId(credentialId.getBytes());
            if (credOpt.isEmpty())
                return Set.of();

            PasskeyCredential cred = credOpt.get();
            return Set.of(
                    RegisteredCredential.builder()
                            .credentialId(new ByteArray(cred.getCredentialId()))
                            .userHandle(new ByteArray(cred.getUserHandle()))
                            .publicKeyCose(new ByteArray(cred.getPublicKeyCose()))
                            .signatureCount(cred.getSignatureCount())
                            .build());
        }
    }
}
