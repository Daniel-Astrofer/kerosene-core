package source.auth.application.usecase.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import source.auth.AuthExceptions;
import source.auth.application.service.account.AppPinService;
import source.auth.application.service.passkey.PasskeyInventoryService;
import source.auth.application.service.security.profile.AdvancedAccountSecurityAvailability;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.AccountSecurityProfileDTO;
import source.auth.dto.AccountSecurityUpdateRequestDTO;
import source.auth.dto.AppPinStatusDTO;
import source.auth.dto.PasskeyActionRequiredDTO;
import source.auth.dto.PasskeyInventoryDTO;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;
import source.common.exception.ErrorCodes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UpdateAccountSecurityProfileUseCaseTest {

    private UserServiceContract userService;
    private PasskeyInventoryService passkeyInventoryService;
    private AdvancedAccountSecurityAvailability advancedAccountSecurityAvailability;
    private AppPinService appPinService;
    private UpdateAccountSecurityProfileUseCase useCase;

    @BeforeEach
    void setUp() {
        userService = mock(UserServiceContract.class);
        passkeyInventoryService = mock(PasskeyInventoryService.class);
        advancedAccountSecurityAvailability = mock(AdvancedAccountSecurityAvailability.class);
        appPinService = mock(AppPinService.class);
        useCase = new UpdateAccountSecurityProfileUseCase(
                userService,
                passkeyInventoryService,
                advancedAccountSecurityAvailability,
                appPinService);
    }

    @Test
    void executeAppliesStandardModeAndBuildsProfile() {
        UserDataBase user = user();
        user.setAccountSecurity(AccountSecurityType.SHAMIR);
        user.setShamirTotalShares(3);
        user.setShamirThreshold(2);
        user.setMultisigThreshold(3);
        AccountSecurityUpdateRequestDTO request = new AccountSecurityUpdateRequestDTO();
        request.setAccountSecurity(AccountSecurityType.STANDARD);
        PasskeyInventoryDTO passkeys = passkeys(true);
        AppPinStatusDTO appPin = appPin();

        when(userService.createUserInDataBase(user)).thenReturn(user);
        when(passkeyInventoryService.inventoryFor(user)).thenReturn(passkeys);
        when(appPinService.getStatus(user, "device-1")).thenReturn(appPin);

        AccountSecurityProfileDTO result = useCase.execute(user, request, "device-1");

        assertEquals(AccountSecurityType.STANDARD, user.getAccountSecurity());
        assertNull(user.getShamirTotalShares());
        assertNull(user.getShamirThreshold());
        assertEquals(2, user.getMultisigThreshold());
        assertEquals(AccountSecurityType.STANDARD, result.accountSecurity());
        assertSame(passkeys, result.passkeys());
        assertSame(appPin, result.appPin());
        verify(advancedAccountSecurityAvailability).assertSupported(AccountSecurityType.STANDARD);
        verify(userService).createUserInDataBase(user);
        verify(passkeyInventoryService).inventoryFor(user);
        verify(appPinService).getStatus(user, "device-1");
    }

    @Test
    void executeAppliesShamirMode() {
        UserDataBase user = user();
        AccountSecurityUpdateRequestDTO request = new AccountSecurityUpdateRequestDTO();
        request.setAccountSecurity(AccountSecurityType.SHAMIR);
        request.setShamirTotalShares(5);
        request.setShamirThreshold(3);
        PasskeyInventoryDTO passkeys = passkeys(false);

        when(userService.createUserInDataBase(user)).thenReturn(user);
        when(passkeyInventoryService.inventoryFor(user)).thenReturn(passkeys);

        AccountSecurityProfileDTO result = useCase.execute(user, request, null);

        assertEquals(AccountSecurityType.SHAMIR, user.getAccountSecurity());
        assertEquals(5, user.getShamirTotalShares());
        assertEquals(3, user.getShamirThreshold());
        assertEquals(2, user.getMultisigThreshold());
        assertEquals(AccountSecurityType.SHAMIR, result.accountSecurity());
    }

    @Test
    void executeRejectsInvalidShamirThresholdWithExistingMessage() {
        UserDataBase user = user();
        AccountSecurityUpdateRequestDTO request = new AccountSecurityUpdateRequestDTO();
        request.setAccountSecurity(AccountSecurityType.SHAMIR);
        request.setShamirTotalShares(3);
        request.setShamirThreshold(4);

        AuthExceptions.InvalidCredentials exception = assertThrows(
                AuthExceptions.InvalidCredentials.class,
                () -> useCase.execute(user, request, null));

        assertEquals("Shamir threshold must be between 2 and total shares.", exception.getMessage());
        verify(userService, never()).createUserInDataBase(user);
    }

    @Test
    void executeAppliesMultisig3FaWhenCurrentLoginHasUsablePasskey() {
        UserDataBase user = user();
        AccountSecurityUpdateRequestDTO request = new AccountSecurityUpdateRequestDTO();
        request.setAccountSecurity(AccountSecurityType.MULTISIG_2FA);
        request.setMultisigThreshold(3);
        PasskeyInventoryDTO passkeys = passkeys(true);

        when(passkeyInventoryService.hasUsablePasskeyForCurrentLogin(user)).thenReturn(true);
        when(userService.createUserInDataBase(user)).thenReturn(user);
        when(passkeyInventoryService.inventoryFor(user)).thenReturn(passkeys);

        AccountSecurityProfileDTO result = useCase.execute(user, request, null);

        assertEquals(AccountSecurityType.MULTISIG_2FA, user.getAccountSecurity());
        assertNull(user.getShamirTotalShares());
        assertNull(user.getShamirThreshold());
        assertEquals(3, user.getMultisigThreshold());
        assertEquals(AccountSecurityType.MULTISIG_2FA, result.accountSecurity());
    }

    @Test
    void executeRejectsMultisig3FaWithoutCompatiblePasskeyWithExistingStructuredError() {
        UserDataBase user = user();
        AccountSecurityUpdateRequestDTO request = new AccountSecurityUpdateRequestDTO();
        request.setAccountSecurity(AccountSecurityType.MULTISIG_2FA);
        request.setMultisigThreshold(3);
        PasskeyActionRequiredDTO guidance = guidance();

        when(passkeyInventoryService.hasUsablePasskeyForCurrentLogin(user)).thenReturn(false);
        when(passkeyInventoryService.buildLinkNewPasskeyGuidance(
                user,
                "Vincule uma passkey deste dispositivo antes de ativar multisig 3FA."))
                .thenReturn(guidance);

        AuthExceptions.StructuredAuthException exception = assertThrows(
                AuthExceptions.StructuredAuthException.class,
                () -> useCase.execute(user, request, null));

        assertEquals("Nenhuma passkey compativel com este login esta vinculada a conta.", exception.getMessage());
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorCodes.AUTH_PASSKEY_LINK_REQUIRED, exception.getErrorCode());
        assertSame(guidance, exception.getData());
        verify(userService, never()).createUserInDataBase(user);
    }

    @Test
    void executeRejectsPasskeyModeWithoutCompatiblePasskeyWithExistingStructuredError() {
        UserDataBase user = user();
        AccountSecurityUpdateRequestDTO request = new AccountSecurityUpdateRequestDTO();
        request.setAccountSecurity(AccountSecurityType.PASSKEY);
        PasskeyActionRequiredDTO guidance = guidance();

        when(passkeyInventoryService.hasUsablePasskeyForCurrentLogin(user)).thenReturn(false);
        when(passkeyInventoryService.buildLinkNewPasskeyGuidance(
                user,
                "Vincule uma passkey deste dispositivo antes de ativar protecao por passkey."))
                .thenReturn(guidance);

        AuthExceptions.StructuredAuthException exception = assertThrows(
                AuthExceptions.StructuredAuthException.class,
                () -> useCase.execute(user, request, null));

        assertEquals("Nenhuma passkey compativel com este login esta vinculada a conta.", exception.getMessage());
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
        assertEquals(ErrorCodes.AUTH_PASSKEY_LINK_REQUIRED, exception.getErrorCode());
        assertSame(guidance, exception.getData());
        verify(userService, never()).createUserInDataBase(user);
    }

    private UserDataBase user() {
        return new UserDataBase();
    }

    private PasskeyInventoryDTO passkeys(boolean registered) {
        return new PasskeyInventoryDTO(registered, registered, false, "localhost", "localhost", List.of());
    }

    private AppPinStatusDTO appPin() {
        return new AppPinStatusDTO(false, false, false, 0, 5, 5, 4, 12, true, true, null, null, null);
    }

    private PasskeyActionRequiredDTO guidance() {
        return new PasskeyActionRequiredDTO("LINK_PASSKEY", "reason", null, true, true, "/auth/passkeys", "guidance", null);
    }
}
