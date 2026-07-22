package source.auth.application.service.account;

import org.springframework.stereotype.Service;
import source.auth.application.service.passkey.PasskeyInventoryService;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.AccountSecurityStatusDTO;
import source.auth.dto.PasskeyInventoryDTO;
import source.auth.model.entity.UserDataBase;

@Service
public class AccountSecurityStatusService {

    private final UserServiceContract userService;
    private final PasskeyInventoryService passkeyInventoryService;

    public AccountSecurityStatusService(
            UserServiceContract userService,
            PasskeyInventoryService passkeyInventoryService) {
        this.userService = userService;
        this.passkeyInventoryService = passkeyInventoryService;
    }

    public AccountSecurityStatusDTO getStatus(Long userId) {
        UserDataBase user = userService.buscarPorId(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found."));
        PasskeyInventoryDTO passkeys = passkeyInventoryService.inventoryFor(user);
        boolean passkeyRegistered = passkeys.passkeyRegistered();
        boolean totpEnabled = user.hasTotpEnabled();
        int backupCodesRemaining = user.getBackupCodes() != null ? user.getBackupCodes().size() : 0;

        return new AccountSecurityStatusDTO(
                user.getPasswordHash() != null && !user.getPasswordHash().isBlank(),
                passkeyRegistered,
                totpEnabled,
                backupCodesRemaining,
                !totpEnabled,
                !totpEnabled
                        ? "Conta nao protegida: ative o TOTP para reduzir o risco de perda ou tomada de conta."
                        : null,
                Boolean.TRUE.equals(user.getIsActive()),
                Boolean.TRUE.equals(user.getIsActive()),
                passkeys);
    }
}
