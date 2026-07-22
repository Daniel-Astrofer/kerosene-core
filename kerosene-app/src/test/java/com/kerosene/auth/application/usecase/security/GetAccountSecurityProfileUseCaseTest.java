package source.auth.application.usecase.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import source.auth.application.service.account.AppPinService;
import source.auth.application.service.passkey.PasskeyInventoryService;
import source.auth.dto.AccountSecurityProfileDTO;
import source.auth.dto.AppPinStatusDTO;
import source.auth.dto.PasskeyInventoryDTO;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetAccountSecurityProfileUseCaseTest {

    private PasskeyInventoryService passkeyInventoryService;
    private AppPinService appPinService;
    private GetAccountSecurityProfileUseCase useCase;

    @BeforeEach
    void setUp() {
        passkeyInventoryService = mock(PasskeyInventoryService.class);
        appPinService = mock(AppPinService.class);
        useCase = new GetAccountSecurityProfileUseCase(passkeyInventoryService, appPinService);
    }

    @Test
    void executeBuildsProfileFromCurrentPasskeyInventoryAndDevicePinStatus() {
        UserDataBase user = user();
        PasskeyInventoryDTO passkeys = passkeys();
        AppPinStatusDTO appPin = appPin();

        when(passkeyInventoryService.inventoryFor(user)).thenReturn(passkeys);
        when(appPinService.getStatus(user, "device-1")).thenReturn(appPin);

        AccountSecurityProfileDTO result = useCase.execute(user, "device-1");

        assertEquals(AccountSecurityType.PASSKEY, result.accountSecurity());
        assertTrue(result.passkeyAvailable());
        assertSame(passkeys, result.passkeys());
        assertSame(appPin, result.appPin());
        verify(passkeyInventoryService).inventoryFor(user);
        verify(appPinService).getStatus(user, "device-1");
    }

    private UserDataBase user() {
        UserDataBase user = new UserDataBase();
        user.setAccountSecurity(AccountSecurityType.PASSKEY);
        user.setMultisigThreshold(2);
        return user;
    }

    private PasskeyInventoryDTO passkeys() {
        return new PasskeyInventoryDTO(true, true, false, "localhost", "localhost", List.of());
    }

    private AppPinStatusDTO appPin() {
        return new AppPinStatusDTO(false, true, false, 0, 5, 5, 4, 12, true, true, null, null, null);
    }
}
