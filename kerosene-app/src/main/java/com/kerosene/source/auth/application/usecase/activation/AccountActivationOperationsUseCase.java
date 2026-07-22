package source.auth.application.usecase.activation;

import org.springframework.stereotype.Component;
import source.auth.application.service.account.AccountActivationService;
import source.auth.dto.AccountActivationStatusDTO;

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
