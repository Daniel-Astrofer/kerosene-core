package source.auth.application.usecase.security;

import org.springframework.stereotype.Component;
import source.auth.application.service.account.AccountSecurityStatusService;
import source.auth.dto.AccountSecurityStatusDTO;

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
