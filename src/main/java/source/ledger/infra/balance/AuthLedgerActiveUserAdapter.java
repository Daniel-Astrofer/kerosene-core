package source.ledger.infra.balance;

import org.springframework.stereotype.Component;
import source.auth.application.service.user.contract.UserServiceContract;
import source.ledger.application.balance.LedgerActiveUser;
import source.ledger.application.balance.LedgerActiveUserPort;

import java.util.List;

@Component
public class AuthLedgerActiveUserAdapter implements LedgerActiveUserPort {

    private final UserServiceContract userService;

    public AuthLedgerActiveUserAdapter(UserServiceContract userService) {
        this.userService = userService;
    }

    @Override
    public List<LedgerActiveUser> listActiveUsers() {
        return userService.listar().stream()
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .map(user -> new LedgerActiveUser(user.getId(), user.getUsername()))
                .toList();
    }
}
