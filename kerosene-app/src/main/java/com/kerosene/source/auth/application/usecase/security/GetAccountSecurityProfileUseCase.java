package source.auth.application.usecase.security;

import org.springframework.stereotype.Component;
import source.auth.application.service.account.AppPinService;
import source.auth.application.service.passkey.PasskeyInventoryService;
import source.auth.dto.AccountSecurityProfileDTO;
import source.auth.dto.PasskeyInventoryDTO;
import source.auth.model.entity.UserDataBase;

@Component
public class GetAccountSecurityProfileUseCase {

    private final PasskeyInventoryService passkeyInventoryService;
    private final AppPinService appPinService;

    public GetAccountSecurityProfileUseCase(
            PasskeyInventoryService passkeyInventoryService,
            AppPinService appPinService) {
        this.passkeyInventoryService = passkeyInventoryService;
        this.appPinService = appPinService;
    }

    public AccountSecurityProfileDTO execute(UserDataBase user, String deviceHash) {
        PasskeyInventoryDTO passkeys = passkeyInventoryService.inventoryFor(user);
        return AccountSecurityProfileDTO.fromUser(
                user,
                passkeys.passkeyRegistered(),
                passkeys,
                appPinService.getStatus(user, deviceHash));
    }
}
