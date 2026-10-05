package com.kerosene.auth.application.usecase.activation;

import org.springframework.stereotype.Component;
import com.kerosene.auth.application.service.account.AccountActivationService;
import com.kerosene.auth.dto.AccountActivationStatusDTO;

/** Exposes account-activation status, link creation, and confirmation as application operations. */
@Component
public class AccountActivationOperationsUseCase {

    /** Domain service implementing activation-link lifecycle and confirmation rules. */
    private final AccountActivationService accountActivationService;

    /** Creates the activation operations facade. */
    /** @param accountActivationService activation lifecycle service */
    public AccountActivationOperationsUseCase(AccountActivationService accountActivationService) {
        this.accountActivationService = accountActivationService;
    }

    /** Retrieves current activation progress for an account. */
    /** @param userId account identifier */
    /** @return activation status projection */
    public AccountActivationStatusDTO getStatus(Long userId) {
        return accountActivationService.getStatus(userId);
    }

    /** Creates a new activation link or reuses the account's current valid link. */
    /** @param userId account identifier */
    /** @return current activation status and link metadata */
    public AccountActivationStatusDTO createOrReuseLink(Long userId) {
        return accountActivationService.createOrReuseLink(userId);
    }

    /** Confirms an activation link using its transaction and source-address evidence. */
    /** @param userId account identifier */
    /** @param linkId activation link identifier */
    /** @param txid transaction identifier supplied as confirmation evidence */
    /** @param fromAddress sending address supplied as confirmation evidence */
    /** @return updated activation status */
    public AccountActivationStatusDTO confirm(Long userId, String linkId, String txid, String fromAddress) {
        return accountActivationService.confirm(userId, linkId, txid, fromAddress);
    }
}
