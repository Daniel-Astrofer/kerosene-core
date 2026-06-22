package source.auth.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import source.auth.application.infra.persistence.jpa.DeviceKeyCredentialRepository;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.orchestrator.signup.FinalizeSignupAccount;
import source.auth.application.service.cache.contracts.RedisServicer;
import source.auth.application.service.devicekey.DeviceKeyChallengeException;
import source.auth.application.service.devicekey.DeviceKeyProtocolException;
import source.auth.application.service.devicekey.DeviceKeyReplayException;
import source.auth.application.service.devicekey.DeviceKeyService;
import source.auth.application.service.util.DevBalanceInjector;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.application.usecase.devicekey.FinishAuthenticatedDeviceKeyRegistrationUseCase;
import source.auth.application.usecase.devicekey.FinishOnboardingDeviceKeyRegistrationUseCase;
import source.auth.application.usecase.devicekey.GetDeviceKeyAuthenticationChallengeUseCase;
import source.auth.application.usecase.devicekey.ManageDeviceKeyDevicesUseCase;
import source.auth.application.usecase.devicekey.StartAuthenticatedDeviceKeyRegistrationUseCase;
import source.auth.application.usecase.devicekey.StartOnboardingDeviceKeyRegistrationUseCase;
import source.auth.dto.devicekey.DeviceKeyChallengeResponse;
import source.auth.dto.devicekey.DeviceKeyDeviceDTO;
import source.auth.dto.devicekey.DeviceKeyRegistrationRequest;
import source.auth.dto.devicekey.DeviceKeyVerifyRequest;
import source.common.dto.ApiResponse;
import source.common.exception.ErrorCodes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceKeyControllerErrorSanitizationTest {

    private static final String SECRET_RUNTIME_MESSAGE = "sql detail: private-device-key-token";

    private final DeviceKeyService deviceKeyService = mock(DeviceKeyService.class);
    private final DeviceKeyCredentialRepository deviceKeyRepository = mock(DeviceKeyCredentialRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final FinalizeSignupAccount finalizeSignupAccount = mock(FinalizeSignupAccount.class);
    private final JwtServicer jwtServicer = mock(JwtServicer.class);
    private final DevBalanceInjector balanceInjector = mock(DevBalanceInjector.class);
    private final RedisServicer redisService = mock(RedisServicer.class);
    private final GetDeviceKeyAuthenticationChallengeUseCase getDeviceKeyAuthenticationChallengeUseCase =
            mock(GetDeviceKeyAuthenticationChallengeUseCase.class);
    private final ManageDeviceKeyDevicesUseCase manageDeviceKeyDevicesUseCase =
            mock(ManageDeviceKeyDevicesUseCase.class);
    private final StartOnboardingDeviceKeyRegistrationUseCase startOnboardingDeviceKeyRegistrationUseCase =
            mock(StartOnboardingDeviceKeyRegistrationUseCase.class);
    private final StartAuthenticatedDeviceKeyRegistrationUseCase startAuthenticatedDeviceKeyRegistrationUseCase =
            mock(StartAuthenticatedDeviceKeyRegistrationUseCase.class);
    private final FinishAuthenticatedDeviceKeyRegistrationUseCase finishAuthenticatedDeviceKeyRegistrationUseCase =
            mock(FinishAuthenticatedDeviceKeyRegistrationUseCase.class);
    private final FinishOnboardingDeviceKeyRegistrationUseCase finishOnboardingDeviceKeyRegistrationUseCase =
            mock(FinishOnboardingDeviceKeyRegistrationUseCase.class);

    private final DeviceKeyController controller = new DeviceKeyController(
            deviceKeyService,
            deviceKeyRepository,
            userRepository,
            finalizeSignupAccount,
            jwtServicer,
            balanceInjector,
            redisService,
            getDeviceKeyAuthenticationChallengeUseCase,
            manageDeviceKeyDevicesUseCase,
            startOnboardingDeviceKeyRegistrationUseCase,
            startAuthenticatedDeviceKeyRegistrationUseCase,
            finishAuthenticatedDeviceKeyRegistrationUseCase,
            finishOnboardingDeviceKeyRegistrationUseCase);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void startOnboardingRegistrationMapsSessionExpired() {
        when(startOnboardingDeviceKeyRegistrationUseCase.execute("session-1", "alice"))
                .thenReturn(StartOnboardingDeviceKeyRegistrationUseCase.Result.sessionExpired());

        ResponseEntity<ApiResponse<DeviceKeyChallengeResponse>> response =
                controller.startOnboardingRegistration("session-1", "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Session expired");
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_SESSION_EXPIRED);
    }

    @Test
    void startOnboardingRegistrationMapsSuccess() {
        DeviceKeyChallengeResponse challenge = new DeviceKeyChallengeResponse(
                "challenge-id",
                "challenge",
                120L,
                "onion",
                "Ed25519",
                "v1");
        when(startOnboardingDeviceKeyRegistrationUseCase.execute("session-1", "alice"))
                .thenReturn(StartOnboardingDeviceKeyRegistrationUseCase.Result.generated(challenge));

        ResponseEntity<ApiResponse<DeviceKeyChallengeResponse>> response =
                controller.startOnboardingRegistration("session-1", "alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Device key challenge generated");
        assertThat(response.getBody().getData()).isEqualTo(challenge);
    }

    @Test
    void finishOnboardingRegistrationMapsSessionExpired() {
        when(finishOnboardingDeviceKeyRegistrationUseCase.execute(eq("session-1"), any(DeviceKeyRegistrationRequest.class)))
                .thenReturn(FinishOnboardingDeviceKeyRegistrationUseCase.Result.sessionExpired());

        ResponseEntity<ApiResponse<String>> response = controller.finishOnboardingRegistration(
                "session-1",
                new DeviceKeyRegistrationRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Session expired");
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_SESSION_EXPIRED);
    }

    @Test
    void finishOnboardingRegistrationMapsSuccess() {
        when(finishOnboardingDeviceKeyRegistrationUseCase.execute(eq("session-1"), any(DeviceKeyRegistrationRequest.class)))
                .thenReturn(FinishOnboardingDeviceKeyRegistrationUseCase.Result.created("42 jwt-token"));

        ResponseEntity<ApiResponse<String>> response = controller.finishOnboardingRegistration(
                "session-1",
                new DeviceKeyRegistrationRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Device key linked and account created.");
        assertThat(response.getBody().getData()).isEqualTo("42 jwt-token");
    }

    @Test
    void finishOnboardingRegistrationDoesNotExposeGenericRuntimeMessage() {
        when(finishOnboardingDeviceKeyRegistrationUseCase.execute(eq("session-1"), any(DeviceKeyRegistrationRequest.class)))
                .thenThrow(new RuntimeException(SECRET_RUNTIME_MESSAGE));

        ResponseEntity<ApiResponse<String>> response = controller.finishOnboardingRegistration(
                "session-1",
                new DeviceKeyRegistrationRequest());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_GENERIC);
        assertThat(response.getBody().getMessage()).isEqualTo("Device key request failed.");
        assertThat(response.getBody().getMessage()).doesNotContain(SECRET_RUNTIME_MESSAGE);
    }

    @Test
    void verifyAndLoginDoesNotExposeGenericRuntimeMessage() {
        DeviceKeyVerifyRequest request = new DeviceKeyVerifyRequest();
        request.setCredentialId("credential-1");
        when(deviceKeyRepository.findByCredentialId("credential-1"))
                .thenThrow(new RuntimeException(SECRET_RUNTIME_MESSAGE));

        ResponseEntity<ApiResponse<Object>> response = controller.verifyAndLogin(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_GENERIC);
        assertThat(response.getBody().getMessage()).isEqualTo("Device key request failed.");
        assertThat(response.getBody().getMessage()).doesNotContain(SECRET_RUNTIME_MESSAGE);
    }

    @Test
    void finishOnboardingRegistrationDoesNotExposeChallengeReason() {
        when(finishOnboardingDeviceKeyRegistrationUseCase.execute(eq("session-1"), any(DeviceKeyRegistrationRequest.class)))
                .thenThrow(new DeviceKeyChallengeException(SECRET_RUNTIME_MESSAGE));

        ResponseEntity<ApiResponse<String>> response = controller.finishOnboardingRegistration(
                "session-1",
                new DeviceKeyRegistrationRequest());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_PASSKEY_CHALLENGE);
        assertThat(response.getBody().getMessage()).isEqualTo("Device key challenge is required or expired.");
        assertThat(response.getBody().getMessage()).doesNotContain(SECRET_RUNTIME_MESSAGE);
    }

    @Test
    void finishOnboardingRegistrationDoesNotExposeProtocolReason() {
        when(finishOnboardingDeviceKeyRegistrationUseCase.execute(eq("session-1"), any(DeviceKeyRegistrationRequest.class)))
                .thenThrow(new DeviceKeyProtocolException(SECRET_RUNTIME_MESSAGE));

        ResponseEntity<ApiResponse<String>> response = controller.finishOnboardingRegistration(
                "session-1",
                new DeviceKeyRegistrationRequest());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_PASSKEY_ASSERTION_FAILED);
        assertThat(response.getBody().getMessage()).isEqualTo("Device key assertion could not be verified.");
        assertThat(response.getBody().getMessage()).doesNotContain(SECRET_RUNTIME_MESSAGE);
    }

    @Test
    void verifyAndLoginDoesNotExposeReplayReason() {
        DeviceKeyVerifyRequest request = new DeviceKeyVerifyRequest();
        request.setCredentialId("credential-1");
        when(deviceKeyRepository.findByCredentialId("credential-1"))
                .thenThrow(new DeviceKeyReplayException(SECRET_RUNTIME_MESSAGE));

        ResponseEntity<ApiResponse<Object>> response = controller.verifyAndLogin(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_PASSKEY_REPLAY);
        assertThat(response.getBody().getMessage()).isEqualTo("Device key request was rejected by replay protection.");
        assertThat(response.getBody().getMessage()).doesNotContain(SECRET_RUNTIME_MESSAGE);
    }

    @Test
    void getChallengeMapsUserNotFound() {
        when(getDeviceKeyAuthenticationChallengeUseCase.execute("alice"))
                .thenReturn(new GetDeviceKeyAuthenticationChallengeUseCase.Result(
                        GetDeviceKeyAuthenticationChallengeUseCase.Status.USER_NOT_FOUND,
                        "User not found",
                        null));

        ResponseEntity<ApiResponse<DeviceKeyChallengeResponse>> response = controller.getChallenge("alice");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("User not found");
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_USER_NOT_FOUND);
    }

    @Test
    void getChallengeMapsSuccess() {
        DeviceKeyChallengeResponse challenge = new DeviceKeyChallengeResponse(
                "challenge-id",
                "challenge",
                120L,
                "onion",
                "Ed25519",
                "v1");
        when(getDeviceKeyAuthenticationChallengeUseCase.execute("  Alice  "))
                .thenReturn(new GetDeviceKeyAuthenticationChallengeUseCase.Result(
                        GetDeviceKeyAuthenticationChallengeUseCase.Status.GENERATED,
                        null,
                        challenge));

        ResponseEntity<ApiResponse<DeviceKeyChallengeResponse>> response = controller.getChallenge("  Alice  ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Device key challenge generated");
        assertThat(response.getBody().getData()).isEqualTo(challenge);
    }

    @Test
    void startAuthenticatedRegistrationRequiresAuthenticatedUser() {
        ResponseEntity<ApiResponse<DeviceKeyChallengeResponse>> response = controller.startAuthenticatedRegistration();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Must be logged in to register a device key");
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_SESSION_EXPIRED);
        verify(startAuthenticatedDeviceKeyRegistrationUseCase, never()).execute(anyLong());
    }

    @Test
    void startAuthenticatedRegistrationMapsUserMissingAsSessionExpired() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "credentials", List.of()));
        when(startAuthenticatedDeviceKeyRegistrationUseCase.execute(42L))
                .thenReturn(new StartAuthenticatedDeviceKeyRegistrationUseCase.Result(
                        StartAuthenticatedDeviceKeyRegistrationUseCase.Status.USER_NOT_FOUND,
                        null));

        ResponseEntity<ApiResponse<DeviceKeyChallengeResponse>> response = controller.startAuthenticatedRegistration();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Must be logged in to register a device key");
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_SESSION_EXPIRED);
    }

    @Test
    void startAuthenticatedRegistrationMapsSuccess() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "credentials", List.of()));
        DeviceKeyChallengeResponse challenge = new DeviceKeyChallengeResponse(
                "challenge-id",
                "challenge",
                120L,
                "onion",
                "Ed25519",
                "v1");
        when(startAuthenticatedDeviceKeyRegistrationUseCase.execute(42L))
                .thenReturn(new StartAuthenticatedDeviceKeyRegistrationUseCase.Result(
                        StartAuthenticatedDeviceKeyRegistrationUseCase.Status.GENERATED,
                        challenge));

        ResponseEntity<ApiResponse<DeviceKeyChallengeResponse>> response = controller.startAuthenticatedRegistration();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Device key registration challenge generated");
        assertThat(response.getBody().getData()).isEqualTo(challenge);
    }

    @Test
    void finishAuthenticatedRegistrationRequiresAuthenticatedUser() {
        ResponseEntity<ApiResponse<String>> response = controller.finishAuthenticatedRegistration(
                new DeviceKeyRegistrationRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Must be logged in to register a device key");
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_SESSION_EXPIRED);
        verify(finishAuthenticatedDeviceKeyRegistrationUseCase, never()).execute(anyLong(), any());
    }

    @Test
    void finishAuthenticatedRegistrationMapsUserMissingAsSessionExpired() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "credentials", List.of()));
        when(finishAuthenticatedDeviceKeyRegistrationUseCase.execute(eq(42L), any(DeviceKeyRegistrationRequest.class)))
                .thenReturn(new FinishAuthenticatedDeviceKeyRegistrationUseCase.Result(
                        FinishAuthenticatedDeviceKeyRegistrationUseCase.Status.USER_NOT_FOUND));

        ResponseEntity<ApiResponse<String>> response = controller.finishAuthenticatedRegistration(
                new DeviceKeyRegistrationRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Must be logged in to register a device key");
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_SESSION_EXPIRED);
    }

    @Test
    void finishAuthenticatedRegistrationMapsChallengeFailure() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "credentials", List.of()));
        when(finishAuthenticatedDeviceKeyRegistrationUseCase.execute(eq(42L), any(DeviceKeyRegistrationRequest.class)))
                .thenThrow(new DeviceKeyChallengeException(SECRET_RUNTIME_MESSAGE));

        ResponseEntity<ApiResponse<String>> response = controller.finishAuthenticatedRegistration(
                new DeviceKeyRegistrationRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Device key challenge is required or expired.");
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_PASSKEY_CHALLENGE);
        assertThat(response.getBody().getMessage()).doesNotContain(SECRET_RUNTIME_MESSAGE);
    }

    @Test
    void finishAuthenticatedRegistrationMapsProtocolFailure() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "credentials", List.of()));
        when(finishAuthenticatedDeviceKeyRegistrationUseCase.execute(eq(42L), any(DeviceKeyRegistrationRequest.class)))
                .thenThrow(new DeviceKeyProtocolException(SECRET_RUNTIME_MESSAGE));

        ResponseEntity<ApiResponse<String>> response = controller.finishAuthenticatedRegistration(
                new DeviceKeyRegistrationRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Device key assertion could not be verified.");
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_PASSKEY_ASSERTION_FAILED);
        assertThat(response.getBody().getMessage()).doesNotContain(SECRET_RUNTIME_MESSAGE);
    }

    @Test
    void finishAuthenticatedRegistrationMapsSuccess() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "credentials", List.of()));
        when(finishAuthenticatedDeviceKeyRegistrationUseCase.execute(eq(42L), any(DeviceKeyRegistrationRequest.class)))
                .thenReturn(new FinishAuthenticatedDeviceKeyRegistrationUseCase.Result(
                        FinishAuthenticatedDeviceKeyRegistrationUseCase.Status.REGISTERED));

        ResponseEntity<ApiResponse<String>> response = controller.finishAuthenticatedRegistration(
                new DeviceKeyRegistrationRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Device key registered successfully");
        assertThat(response.getBody().getData()).isEqualTo("OK");
    }

    @Test
    void getRegisteredDevicesRequiresAuthenticatedUser() {
        ResponseEntity<ApiResponse<List<DeviceKeyDeviceDTO>>> response = controller.getRegisteredDevices();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Must be logged in to inspect device keys");
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_SESSION_EXPIRED);
        verify(manageDeviceKeyDevicesUseCase, never()).listDevices(anyLong());
    }

    @Test
    void getRegisteredDevicesMapsUserMissingAsSessionExpired() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "credentials", List.of()));
        when(manageDeviceKeyDevicesUseCase.listDevices(42L))
                .thenReturn(new ManageDeviceKeyDevicesUseCase.Result(
                        ManageDeviceKeyDevicesUseCase.Status.USER_NOT_FOUND,
                        null));

        ResponseEntity<ApiResponse<List<DeviceKeyDeviceDTO>>> response = controller.getRegisteredDevices();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Must be logged in to inspect device keys");
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_SESSION_EXPIRED);
    }

    @Test
    void getRegisteredDevicesMapsSuccess() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "credentials", List.of()));
        List<DeviceKeyDeviceDTO> devices = List.of();
        when(manageDeviceKeyDevicesUseCase.listDevices(42L))
                .thenReturn(new ManageDeviceKeyDevicesUseCase.Result(
                        ManageDeviceKeyDevicesUseCase.Status.LISTED,
                        devices));

        ResponseEntity<ApiResponse<List<DeviceKeyDeviceDTO>>> response = controller.getRegisteredDevices();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Registered device keys retrieved.");
        assertThat(response.getBody().getData()).isEqualTo(devices);
    }

    @Test
    void revokeDeviceRequiresAuthenticatedUser() {
        ResponseEntity<ApiResponse<List<DeviceKeyDeviceDTO>>> response = controller.revokeDevice("credential-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Must be logged in to revoke device keys");
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_SESSION_EXPIRED);
        verify(manageDeviceKeyDevicesUseCase, never()).revokeDevice(anyLong(), any());
    }

    @Test
    void revokeDeviceMapsUserMissingAsSessionExpired() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "credentials", List.of()));
        when(manageDeviceKeyDevicesUseCase.revokeDevice(42L, "credential-1"))
                .thenReturn(new ManageDeviceKeyDevicesUseCase.Result(
                        ManageDeviceKeyDevicesUseCase.Status.USER_NOT_FOUND,
                        null));

        ResponseEntity<ApiResponse<List<DeviceKeyDeviceDTO>>> response = controller.revokeDevice("credential-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Must be logged in to revoke device keys");
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_SESSION_EXPIRED);
    }

    @Test
    void revokeDeviceMapsMissingCredential() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "credentials", List.of()));
        when(manageDeviceKeyDevicesUseCase.revokeDevice(42L, "credential-1"))
                .thenReturn(new ManageDeviceKeyDevicesUseCase.Result(
                        ManageDeviceKeyDevicesUseCase.Status.CREDENTIAL_NOT_FOUND,
                        null));

        ResponseEntity<ApiResponse<List<DeviceKeyDeviceDTO>>> response = controller.revokeDevice("credential-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Device key not found");
        assertThat(response.getBody().getErrorCode()).isEqualTo(ErrorCodes.AUTH_PASSKEY_CREDENTIAL_NOT_FOUND);
    }

    @Test
    void revokeDeviceMapsSuccess() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "credentials", List.of()));
        List<DeviceKeyDeviceDTO> devices = List.of();
        when(manageDeviceKeyDevicesUseCase.revokeDevice(42L, "credential-1"))
                .thenReturn(new ManageDeviceKeyDevicesUseCase.Result(
                        ManageDeviceKeyDevicesUseCase.Status.REVOKED,
                        devices));

        ResponseEntity<ApiResponse<List<DeviceKeyDeviceDTO>>> response = controller.revokeDevice("credential-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Device key revoked.");
        assertThat(response.getBody().getData()).isEqualTo(devices);
    }

}
