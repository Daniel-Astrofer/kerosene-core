package source.auth.application.service.account;

import org.springframework.stereotype.Service;
import source.auth.application.infra.persistence.jpa.PasskeyCredentialRepository;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.dto.AccountSecurityStatusDTO;
import source.auth.model.entity.UserDataBase;

@Service
public class AccountSecurityStatusService {

    private final UserServiceContract userService;
    private final PasskeyCredentialRepository passkeyCredentialRepository;

    public AccountSecurityStatusService(
            UserServiceContract userService,
            PasskeyCredentialRepository passkeyCredentialRepository) {
        this.userService = userService;
        this.passkeyCredentialRepository = passkeyCredentialRepository;
    }

    public AccountSecurityStatusDTO getStatus(Long userId) {
        UserDataBase user = userService.buscarPorId(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found."));
        boolean passkeyRegistered = !passkeyCredentialRepository.findByUserId(user.getId()).isEmpty();
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
                Boolean.TRUE.equals(user.getIsActive()));
    }
}
