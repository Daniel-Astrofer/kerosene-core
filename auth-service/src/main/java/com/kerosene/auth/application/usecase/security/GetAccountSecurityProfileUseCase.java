package com.kerosene.auth.application.usecase.security;

import org.springframework.stereotype.Component;
import com.kerosene.auth.application.service.account.AppPinService;
import com.kerosene.auth.application.service.passkey.PasskeyInventoryService;
import com.kerosene.auth.dto.AccountSecurityProfileDTO;
import com.kerosene.auth.dto.PasskeyInventoryDTO;
import com.kerosene.auth.model.entity.UserDataBase;

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
