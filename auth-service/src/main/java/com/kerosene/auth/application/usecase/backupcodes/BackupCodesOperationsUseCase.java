package com.kerosene.auth.application.usecase.backupcodes;

import org.springframework.stereotype.Component;
import com.kerosene.auth.AuthExceptions;
import com.kerosene.auth.application.service.account.BackupCodeService;
import com.kerosene.auth.application.service.identityaccess.TransactionalAuthenticationPort;
import com.kerosene.auth.application.service.identityaccess.TransactionalAuthenticationRequest;
import com.kerosene.auth.dto.BackupCodesStatusDTO;

@Component
public class BackupCodesOperationsUseCase {

    private static final String REGENERATION_AUTHORIZATION_FAILURE =
            "Unable to authorize backup code regeneration.";

    private final BackupCodeService backupCodeService;
    private final TransactionalAuthenticationPort transactionalAuthenticationPort;

    public BackupCodesOperationsUseCase(
            BackupCodeService backupCodeService,
            TransactionalAuthenticationPort transactionalAuthenticationPort) {
        this.backupCodeService = backupCodeService;
        this.transactionalAuthenticationPort = transactionalAuthenticationPort;
    }

    public BackupCodesStatusDTO getStatus(Long userId) {
        return backupCodeService.getStatus(userId);
    }

    public BackupCodesStatusDTO regenerate(
            Long userId,
            String totpCode,
            String passkeyAssertionJson,
            String confirmationPassphrase) {
        try {
            transactionalAuthenticationPort.authorize(TransactionalAuthenticationRequest.accountSecurityChange(
                    userId,
                    totpCode,
                    passkeyAssertionJson,
                    confirmationPassphrase));
        } catch (AuthExceptions.AuthValidationException exception) {
            throw new AuthExceptions.InvalidCredentials(REGENERATION_AUTHORIZATION_FAILURE);
        }

        return backupCodeService.regenerate(userId);
    }
}
