package source.wallet.infra;

import org.springframework.stereotype.Component;
import source.auth.application.service.user.contract.UserServiceContract;
import source.auth.model.entity.UserDataBase;
import source.wallet.application.port.out.WalletUserPort;

@Component
public class WalletUserAdapter implements WalletUserPort {

    private final UserServiceContract userService;

    public WalletUserAdapter(UserServiceContract userService) {
        this.userService = userService;
    }

    @Override
    public UserDataBase requireUser(Long userId) {
        return userService.buscarPorId(userId)
                .orElseThrow(() -> new IllegalArgumentException("invalid user"));
    }
}
