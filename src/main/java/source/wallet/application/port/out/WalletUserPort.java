package source.wallet.application.port.out;

import source.auth.model.entity.UserDataBase;

public interface WalletUserPort {

    UserDataBase requireUser(Long userId);
}
