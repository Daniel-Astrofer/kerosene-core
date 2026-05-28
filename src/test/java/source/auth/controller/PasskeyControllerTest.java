package source.auth.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import source.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import source.auth.application.infra.persistence.jpa.PasskeyVerificationProjection;
import source.auth.application.infra.persistence.jpa.UserRepository;
import source.auth.application.orchestrator.signup.FinalizeSignupAccount;
import source.auth.application.orchestrator.signup.port.SignupStateStore;
import source.auth.application.service.passkey.PasskeyInventoryService;
import source.auth.application.service.passkey.PasskeyService;
import source.auth.application.service.util.DevBalanceInjector;
import source.auth.application.service.validation.jwt.contracts.JwtServicer;
import source.auth.dto.SignupState;
import source.auth.model.entity.UserDataBase;
import source.common.dto.ApiResponse;
import source.common.exception.ErrorCodes;
import source.transactions.exception.ExternalPaymentsExceptions;

import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasskeyControllerTest {

    private PasskeyService passkeyService;
    private PasskeyCredentialRepository passkeyCredentialRepository;
    private UserRepository userRepository;
    private PasskeyInventoryService passkeyInventoryService;
    private DevBalanceInjector balanceInjector;
    private SignupStateStore signupStateStore;
    private FinalizeSignupAccount finalizeSignupAccount;
    private JwtServicer jwtServicer;
    private PasskeyController controller;

    @BeforeEach
    void setUp() {
        passkeyService = mock(PasskeyService.class);
        passkeyCredentialRepository = mock(PasskeyCredentialRepository.class);
        userRepository = mock(UserRepository.class);
        passkeyInventoryService = mock(PasskeyInventoryService.class);
        balanceInjector = mock(DevBalanceInjector.class);
        signupStateStore = mock(SignupStateStore.class);
        finalizeSignupAccount = mock(FinalizeSignupAccount.class);
        jwtServicer = mock(JwtServicer.class);

        controller = new PasskeyController(
                passkeyService,
                passkeyCredentialRepository,
                userRepository,
                jwtServicer,
                signupStateStore,
                passkeyInventoryService,
                balanceInjector,
                finalizeSignupAccount);
    }

    @Test
    void passkeyChallengeNormalizesUsernameLikePasswordLogin() {
        when(passkeyService.generateChallenge("manobrow")).thenReturn("challenge-hex");

        ResponseEntity<ApiResponse<String>> response = controller.getChallenge("  ManoBrow  ");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("challenge-hex", response.getBody().getData());
        verify(passkeyService).generateChallenge("manobrow");
    }

    @Test
    void passkeyVerifyResolvesUserByCredentialIdWhenUsernameIsMissing() {
        byte[] credentialId = new byte[] { 1, 2, 3 };
        UserDataBase user = new UserDataBase();
        setUserId(user, 42L);
        user.setUsername("alice");

        PasskeyVerificationProjection credential = new PasskeyVerificationProjection(
                credentialId,
                new byte[32],
                0L,
                "ACTIVE",
                "localhost",
                "localhost",
                42L,
                "alice",
                true);

        PasskeyController.PasskeyVerifyRequest request = new PasskeyController.PasskeyVerifyRequest();
        request.setCredentialId(Base64.getEncoder().encodeToString(credentialId));
        request.setSignature("signature");
        request.setAuthData("auth-data");
        request.setClientDataJSON("client-data");

        when(passkeyCredentialRepository.findVerificationByCredentialId(any(byte[].class)))
                .thenReturn(Optional.of(credential));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));
        when(passkeyInventoryService.isKnownIncompatibleForCurrentLogin("localhost", "localhost")).thenReturn(false);
        when(passkeyService.isClientDataOriginAllowed("client-data")).thenReturn(true);
        when(passkeyService.consumeChallengeFromRedis("alice")).thenReturn("challenge-hex");
        when(passkeyService.verifyAuthenticationAssertion(
                eq("alice"),
                eq("challenge-hex"),
                eq("signature"),
                any(byte[].class),
                eq("auth-data"),
                eq("client-data"))).thenReturn(new PasskeyService.PasskeyVerificationResult(true, 1L));
        when(passkeyCredentialRepository.advanceSignatureCount(eq(credentialId), eq(42L), eq(1L)))
                .thenReturn(1);
        when(jwtServicer.generateToken(42L)).thenReturn("jwt.token.value");

        ResponseEntity<ApiResponse<Object>> response = controller.verifyAndLogin(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("jwt.token.value", response.getBody().getData());
        verify(userRepository, never()).findByUsername(any());
        verify(passkeyCredentialRepository).findVerificationByCredentialId(any(byte[].class));
        verify(passkeyService).consumeChallengeFromRedis("alice");
    }

    @Test
    void onboardingFinishRejectsInvalidOriginBeforeConsumingChallenge() {
        SignupState state = signupState("alice");
        when(signupStateStore.findSignupState("session-1")).thenReturn(state);
        when(passkeyService.isClientDataOriginAllowed("client-data")).thenReturn(false);
        when(passkeyService.extractOriginFromClientData("client-data"))
                .thenReturn("android:apk-key-hash:kerosene");

        ResponseEntity<ApiResponse<String>> response = controller.finishOnboardingRegistration(
                "session-1",
                registrationRequest("client-data"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(ErrorCodes.AUTH_PASSKEY_INVALID_ORIGIN, response.getBody().getErrorCode());
        verify(passkeyService, never()).consumeChallengeFromRedis("alice");
        verify(passkeyService, never()).getChallengeFromRedis("alice");
        verify(finalizeSignupAccount, never()).execute(any());
    }

    @Test
    void onboardingFinishAcceptsConfiguredAndroidOriginAndFinalizesSignup() {
        SignupState state = signupState("alice");
        UserDataBase user = new UserDataBase();
        setUserId(user, 42L);
        user.setUsername("alice");

        when(signupStateStore.findSignupState("session-1")).thenReturn(state);
        when(passkeyService.isClientDataOriginAllowed("android-client-data")).thenReturn(true);
        when(passkeyService.consumeChallengeFromRedis("alice")).thenReturn("challenge-hex");
        when(passkeyService.verifyRegistrationSignature(
                eq("alice"),
                eq("challenge-hex"),
                eq("signature"),
                any(byte[].class),
                eq("auth-data"),
                eq("android-client-data"))).thenReturn(true);
        when(passkeyService.resolveRelyingPartyIdFromClientData("android-client-data"))
                .thenReturn("epef24frbttdyirb45zif4smrkmhfd4di34my7wdhadzomfcpcf5fbyd.onion");
        when(passkeyService.extractOriginHostFromClientData("android-client-data"))
                .thenReturn("android:apk-key-hash:kerosene");
        when(finalizeSignupAccount.execute("session-1")).thenReturn(user);
        when(jwtServicer.generateToken(42L)).thenReturn("jwt-token");

        ResponseEntity<ApiResponse<String>> response = controller.finishOnboardingRegistration(
                "session-1",
                registrationRequest("android-client-data"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("42 jwt-token", response.getBody().getData());
        verify(passkeyService).consumeChallengeFromRedis("alice");
        verify(passkeyService, never()).deleteChallengeFromRedis("alice");
        verify(finalizeSignupAccount).execute("session-1");
    }

    @Test
    void onboardingFinishConsumesChallengeWhenFinalizationDependencyIsUnavailable() {
        SignupState state = signupState("alice");

        when(signupStateStore.findSignupState("session-1")).thenReturn(state);
        when(passkeyService.isClientDataOriginAllowed("android-client-data")).thenReturn(true);
        when(passkeyService.consumeChallengeFromRedis("alice")).thenReturn("challenge-hex");
        when(passkeyService.verifyRegistrationSignature(
                eq("alice"),
                eq("challenge-hex"),
                eq("signature"),
                any(byte[].class),
                eq("auth-data"),
                eq("android-client-data"))).thenReturn(true);
        when(passkeyService.resolveRelyingPartyIdFromClientData("android-client-data"))
                .thenReturn("epef24frbttdyirb45zif4smrkmhfd4di34my7wdhadzomfcpcf5fbyd.onion");
        when(passkeyService.extractOriginHostFromClientData("android-client-data"))
                .thenReturn("android:apk-key-hash:kerosene");
        when(finalizeSignupAccount.execute("session-1"))
                .thenThrow(new ExternalPaymentsExceptions.CustodyProviderUnavailable("custody unavailable"));

        assertThrows(
                ExternalPaymentsExceptions.CustodyProviderUnavailable.class,
                () -> controller.finishOnboardingRegistration(
                    "session-1",
                    registrationRequest("android-client-data")));

        verify(passkeyService).consumeChallengeFromRedis("alice");
        verify(passkeyService, never()).deleteChallengeFromRedis("alice");
    }

    private SignupState signupState(String username) {
        SignupState state = new SignupState();
        state.setUsername(username);
        return state;
    }

    private PasskeyController.PasskeyRegistrationRequest registrationRequest(String clientDataJson) {
        PasskeyController.PasskeyRegistrationRequest request =
                new PasskeyController.PasskeyRegistrationRequest();
        request.setClientDataJSON(clientDataJson);
        request.setPublicKeyCose(Base64.getEncoder().encodeToString(new byte[32]));
        request.setSignature("signature");
        request.setAuthData("auth-data");
        request.setCredentialId("credential-id");
        request.setUserHandle("user-handle");
        request.setDeviceName("Android");
        return request;
    }

    private void setUserId(UserDataBase user, Long id) {
        try {
            java.lang.reflect.Field field = UserDataBase.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
