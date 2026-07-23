package com.kerosene.auth.application.usecase.activation;

import org.springframework.stereotype.Component;
import com.kerosene.auth.application.service.account.AccountActivationService;
import com.kerosene.auth.dto.AccountActivationStatusDTO;

@Component
public class AccountActivationOperationsUseCase {

    private final AccountActivationService accountActivationService;

    public AccountActivationOperationsUseCase(AccountActivationService accountActivationService) {
        this.accountActivationService = accountActivationService;
    }

    public AccountActivationStatusDTO getStatus(Long userId) {
        return accountActivationService.getStatus(userId);
    }

    public AccountActivationStatusDTO createOrReuseLink(Long userId) {
        return accountActivationService.createOrReuseLink(userId);
    }

    public AccountActivationStatusDTO confirm(Long userId, String linkId, String txid, String fromAddress) {
        return accountActivationService.confirm(userId, linkId, txid, fromAddress);
    }
}
