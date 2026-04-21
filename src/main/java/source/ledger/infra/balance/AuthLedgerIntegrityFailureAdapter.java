package source.ledger.infra.balance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import source.auth.application.service.user.contract.UserServiceContract;
import source.ledger.application.balance.LedgerIntegrityFailure;
import source.ledger.application.balance.LedgerIntegrityFailurePort;

@Component
public class AuthLedgerIntegrityFailureAdapter implements LedgerIntegrityFailurePort {

    private static final Logger log = LoggerFactory.getLogger(AuthLedgerIntegrityFailureAdapter.class);

    private final UserServiceContract userService;

    public AuthLedgerIntegrityFailureAdapter(UserServiceContract userService) {
        this.userService = userService;
    }

    @Override
    public void reportIntegrityFailure(LedgerIntegrityFailure failure) {
        if (failure.userId() == null) {
            log.warn("[LedgerIntegrity] Integrity failure has no user id: ledgerId={}, walletId={}",
                    failure.ledgerId(),
                    failure.walletId());
            return;
        }

        userService.buscarPorId(failure.userId()).ifPresentOrElse(user -> {
            user.setIsActive(false);
            userService.createUserInDataBase(user);
            log.error("[LedgerIntegrity] Disabled user {} after ledger integrity failure on ledger {}",
                    failure.userId(),
                    failure.ledgerId());
        }, () -> log.warn("[LedgerIntegrity] Could not disable missing user {} after ledger integrity failure",
                failure.userId()));
    }
}
