package com.kerosene.auth.application.usecase.security;

import org.springframework.stereotype.Component;
import com.kerosene.auth.application.service.account.AccountSecurityStatusService;
import com.kerosene.auth.dto.AccountSecurityStatusDTO;

@Component
public class GetAccountSecurityStatusUseCase {

    private final AccountSecurityStatusService accountSecurityStatusService;

    public GetAccountSecurityStatusUseCase(AccountSecurityStatusService accountSecurityStatusService) {
        this.accountSecurityStatusService = accountSecurityStatusService;
    }

    public AccountSecurityStatusDTO execute(Long userId) {
        return accountSecurityStatusService.getStatus(userId);
    }
}
