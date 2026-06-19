package source.auth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import source.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.orchestrator.signup.FinalizeSignupAccount;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.service.devicekey.DeviceKeyChallengeException;
import source.auth.application.service.devicekey.DeviceKeyProtocolException;
import source.auth.application.service.devicekey.DeviceKeyReplayException;
import source.auth.application.service.devicekey.DeviceKeyService;
import source.auth.application.service.util.DevBalanceInjector;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.orchestrator.login.StartLogin;
import source.auth.dto.SignupState;
import source.auth.dto.devicekey.DeviceKeyChallengeResponse;
import source.auth.dto.devicekey.DeviceKeyDeviceDTO;
import source.auth.dto.devicekey.DeviceKeyRegistrationRequest;
import source.auth.dto.devicekey.DeviceKeyVerifyRequest;
import source.auth.model.entity.DeviceKeyCredential;
import source.auth.model.entity.UserDataBase;
import source.common.dto.ApiResponse;
import source.common.exception.ErrorCodes;
import source.kfe.rail.KfeRailException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/auth/device-key")
public class DeviceKeyController {

    private static final Logger log = LoggerFactory.getLogger(DeviceKeyController.class);

    private final DeviceKeyService deviceKeyService;
    private final DeviceKeyCredentialRepository deviceKeyRepository;
    private final UserRepository userRepository;
    private final SignupStateStore signupStateStore;
    private final FinalizeSignupAccount finalizeSignupAccount;
    private final JwtServicer jwtServicer;
    private final DevBalanceInjector balanceInjector;
    private final RedisServicer redisService;

    public DeviceKeyController(
            DeviceKeyService deviceKeyService,
            DeviceKeyCredentialRepository deviceKeyRepository,
            UserRepository userRepository,
            SignupStateStore signupStateStore,
            FinalizeSignupAccount finalizeSignupAccount,
            JwtServicer jwtServicer,
            DevBalanceInjector balanceInjector,
            RedisServicer redisService) {
        this.deviceKeyService = deviceKeyService;
        this.deviceKeyRepository = deviceKeyRepository;
        this.userRepository = userRepository;
        this.signupStateStore = signupStateStore;
        this.finalizeSignupAccount = finalizeSignupAccount;
        this.jwtServicer = jwtServicer;
        this.balanceInjector = balanceInjector;
        this.redisService = redisService;
    }

    @PostMapping("/onboarding/start")
    public ResponseEntity<ApiResponse<DeviceKeyChallengeResponse>> startOnboardingRegistration(
            @RequestParam String sessionId,
            @RequestParam(required = false) String username) {
        SignupState state = signupStateStore.findSignupState(sessionId);
        if (state == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Session expired", ErrorCodes.AUTH_SESSION_EXPIRED));
        }

        if ((state.getUsername() == null || state.getUsername().isBlank())
                && username != null && !username.isBlank()) {
            state.setUsername(normalizeUsername(username));
            signupStateStore.saveSignupState(sessionId, state, Duration.ofMinutes(1440));
        }

        DeviceKeyChallengeResponse challenge = deviceKeyService.startRegistrationChallenge(
                sessionId,
                state.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Device key challenge generated", challenge));
    }

    @PostMapping("/onboarding/finish")
    @Transactional
    public ResponseEntity<ApiResponse<String>> finishOnboardingRegistration(
            @RequestParam String sessionId,
            @RequestBody DeviceKeyRegistrationRequest request) {
        SignupState state = signupStateStore.findSignupState(sessionId);
        if (state == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Session expired", ErrorCodes.AUTH_SESSION_EXPIRED));
        }

        try {
            DeviceKeyService.VerifiedDeviceKeyRegistration verified =
                    deviceKeyService.verifyRegistration(request, sessionId, state.getUsername());

            state.setDeviceKeyRegistered(true);
            state.setPasskeyRegistered(true);
            signupStateStore.saveSignupState(sessionId, state, Duration.ofMinutes(1440));

            UserDataBase user = finalizeSignupAccount.execute(sessionId);
            persistDeviceKey(user, verified);

            String token = user.getId() + " " + jwtServicer.generateToken(user.getId());
            return ResponseEntity.ok(ApiResponse.success(
                    "Device key linked and account created.",
                    token));
        } catch (DeviceKeyChallengeException exception) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(ApiResponse.error(exception.getMessage(), ErrorCodes.AUTH_PASSKEY_CHALLENGE));
        } catch (DeviceKeyProtocolException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(exception.getMessage(), ErrorCodes.AUTH_PASSKEY_ASSERTION_FAILED));
        } catch (KfeRailException.ProviderUnavailable
                 | FinalizeSignupAccount.VaultNotReadyException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Device key onboarding failed", exception);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(exception.getMessage(), ErrorCodes.AUTH_GENERIC));
        }
    }

    @GetMapping("/challenge")
    public ResponseEntity<ApiResponse<DeviceKeyChallengeResponse>> getChallenge(@RequestParam String username) {
        UserDataBase user = userRepository.findByUsername(normalizeUsername(username));
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found", ErrorCodes.AUTH_USER_NOT_FOUND));
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Device key challenge generated",
                deviceKeyService.startAuthenticationChallenge(user)));
    }

    @PostMapping("/register/start")
    public ResponseEntity<ApiResponse<DeviceKeyChallengeResponse>> startAuthenticatedRegistration() {
        UserDataBase user = currentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Must be logged in to register a device key", ErrorCodes.AUTH_SESSION_EXPIRED));
        }
        return ResponseEntity.ok(ApiResponse.success(
                "Device key registration challenge generated",
                deviceKeyService.startAuthenticatedRegistrationChallenge(user)));
    }

    @PostMapping("/register/finish")
    @Transactional
    public ResponseEntity<ApiResponse<String>> finishAuthenticatedRegistration(
            @RequestBody DeviceKeyRegistrationRequest request) {
        UserDataBase user = currentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Must be logged in to register a device key", ErrorCodes.AUTH_SESSION_EXPIRED));
        }
        try {
            DeviceKeyService.VerifiedDeviceKeyRegistration verified =
                    deviceKeyService.verifyRegistration(request, "", user.getUsername());
            persistDeviceKey(user, verified);
            return ResponseEntity.ok(ApiResponse.success("Device key registered successfully", "OK"));
        } catch (DeviceKeyChallengeException exception) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(ApiResponse.error(exception.getMessage(), ErrorCodes.AUTH_PASSKEY_CHALLENGE));
        } catch (DeviceKeyProtocolException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(exception.getMessage(), ErrorCodes.AUTH_PASSKEY_ASSERTION_FAILED));
        }
    }

    @PostMapping("/verify")
    @Transactional
    public ResponseEntity<ApiResponse<Object>> verifyAndLogin(@RequestBody DeviceKeyVerifyRequest request) {
        try {
            if (request.getCredentialId() == null || request.getCredentialId().isBlank()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("credentialId is required", ErrorCodes.SYS_INVALID_ARGUMENTS));
            }

            UserDataBase user;
            Optional<DeviceKeyCredential> credentialOpt;
            String normalizedUsername = normalizeUsername(request.getUsername());
            if (normalizedUsername.isBlank()) {
                credentialOpt = deviceKeyRepository.findByCredentialId(request.getCredentialId().trim());
                if (credentialOpt.isEmpty()) {
                    return credentialNotFound();
                }
                user = credentialOpt.get().getUser();
            } else {
                user = userRepository.findByUsername(normalizedUsername);
                if (user == null) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("User not found", ErrorCodes.AUTH_USER_NOT_FOUND));
                }
                credentialOpt = deviceKeyRepository.findByCredentialIdAndUserId(
                        request.getCredentialId().trim(),
                        user.getId());
            }

            if (credentialOpt.isEmpty()) {
                return credentialNotFound();
            }

            DeviceKeyCredential credential = credentialOpt.get();
            long newCounter = deviceKeyService.verifyAuthentication(request, user, credential);
            int updated = deviceKeyRepository.advanceCounter(
                    credential.getCredentialId(),
                    user.getId(),
                    newCounter,
                    LocalDateTime.now());
            if (updated != 1) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error(
                                "Device key counter did not advance.",
                                ErrorCodes.AUTH_PASSKEY_REPLAY));
            }

            finalizeSignupAccount.ensureUserFinancialsReady(user, null);
            
            if (!Boolean.TRUE.equals(user.getIsActive())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("Account is inactive", ErrorCodes.AUTH_INVALID_CREDENTIALS));
            }

            if (user.hasTotpEnabled()) {
                String preAuthToken = UUID.randomUUID().toString();
                redisService.setValue(StartLogin.preAuthKey(preAuthToken), user.getUsername(), StartLogin.PRE_AUTH_TTL_SECONDS);
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(ApiResponse.success("Device key verified. TOTP required.", preAuthToken));
            }

            balanceInjector.injectTestBalance(user);

            String token = jwtServicer.generateToken(user.getId());
            return ResponseEntity.ok(ApiResponse.success("Device key authentication successful", token));
        } catch (DeviceKeyReplayException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(exception.getMessage(), ErrorCodes.AUTH_PASSKEY_REPLAY));
        } catch (DeviceKeyChallengeException exception) {
            return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                    .body(ApiResponse.error(exception.getMessage(), ErrorCodes.AUTH_PASSKEY_CHALLENGE));
        } catch (DeviceKeyProtocolException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(exception.getMessage(), ErrorCodes.AUTH_PASSKEY_ASSERTION_FAILED));
        } catch (RuntimeException exception) {
            log.error("Device key verification failed", exception);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(exception.getMessage(), ErrorCodes.AUTH_GENERIC));
        }
    }

    @GetMapping("/devices")
    public ResponseEntity<ApiResponse<List<DeviceKeyDeviceDTO>>> getRegisteredDevices() {
        UserDataBase user = currentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Must be logged in to inspect device keys", ErrorCodes.AUTH_SESSION_EXPIRED));
        }
        List<DeviceKeyDeviceDTO> devices = deviceKeyRepository.findByUserId(user.getId()).stream()
                .map(DeviceKeyDeviceDTO::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Registered device keys retrieved.", devices));
    }

    @PostMapping("/devices/{credentialId}/revoke")
    public ResponseEntity<ApiResponse<List<DeviceKeyDeviceDTO>>> revokeDevice(@PathVariable String credentialId) {
        UserDataBase user = currentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Must be logged in to revoke device keys", ErrorCodes.AUTH_SESSION_EXPIRED));
        }
        Optional<DeviceKeyCredential> credential =
                deviceKeyRepository.findByCredentialIdAndUserId(credentialId, user.getId());
        if (credential.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Device key not found", ErrorCodes.AUTH_PASSKEY_CREDENTIAL_NOT_FOUND));
        }
        DeviceKeyCredential deviceKey = credential.get();
        deviceKey.setStatus("REVOKED");
        deviceKey.setRevokedAt(LocalDateTime.now());
        deviceKeyRepository.save(deviceKey);
        List<DeviceKeyDeviceDTO> devices = deviceKeyRepository.findByUserId(user.getId()).stream()
                .map(DeviceKeyDeviceDTO::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Device key revoked.", devices));
    }

    private void persistDeviceKey(
            UserDataBase user,
            DeviceKeyService.VerifiedDeviceKeyRegistration verified) {
        if (deviceKeyRepository.findByCredentialIdAndUserId(verified.credentialId(), user.getId()).isPresent()) {
            return;
        }
        DeviceKeyCredential credential = new DeviceKeyCredential();
        credential.setUser(user);
        credential.setCredentialId(verified.credentialId());
        credential.setUserHandle(verified.userHandle());
        credential.setPublicKeyEd25519(verified.publicKeyEd25519());
        credential.setAlgorithm(DeviceKeyService.ALGORITHM);
        credential.setCounter(verified.counter());
        credential.setDeviceName(verified.deviceName());
        credential.setDeviceInstallId(verified.deviceInstallId());
        credential.setKeyStorage(verified.keyStorage());
        credential.setPlatform(verified.platform());
        credential.setBrowser(verified.browser());
        credential.setBrand(verified.brand());
        credential.setModel(verified.model());
        credential.setSerialNumber(verified.serialNumber());
        credential.setOnionServiceId(verified.onionServiceId());
        credential.setProtocolVersion(1);
        credential.setStatus("ACTIVE");
        deviceKeyRepository.save(credential);
    }

    private ResponseEntity<ApiResponse<Object>> credentialNotFound() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(
                        "Esta chave deste dispositivo nao esta vinculada a conta.",
                        ErrorCodes.AUTH_PASSKEY_CREDENTIAL_NOT_FOUND));
    }

    private UserDataBase currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return null;
        }
        return userRepository.findById(Long.parseLong(auth.getName())).orElse(null);
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
