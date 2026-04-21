package source.ledger.infra.balance;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import source.auth.application.service.cripto.contracts.Hasher;
import source.ledger.application.balance.LedgerHashPort;

@Component
public class AuthLedgerHashAdapter implements LedgerHashPort {

    private final Hasher hasher;

    public AuthLedgerHashAdapter(@Qualifier("SHAHasher") Hasher hasher) {
        this.hasher = hasher;
    }

    @Override
    public String hash(char[] data) {
        return hasher.hash(data);
    }
}
