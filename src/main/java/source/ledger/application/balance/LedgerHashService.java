package source.ledger.application.balance;

import org.springframework.stereotype.Service;
import source.ledger.entity.LedgerEntity;

@Service
public class LedgerHashService {

    private final LedgerHashPort hashPort;

    public LedgerHashService(LedgerHashPort hashPort) {
        this.hashPort = hashPort;
    }

    public String generateInitialHash(Long walletId) {
        String data = "GENESIS_" + walletId + "_" + System.currentTimeMillis();
        return hashPort.hash(data.toCharArray());
    }

    public String generateHash(LedgerEntity ledger) {
        String data = ledger.getWallet().getId() + "_"
                + ledger.getBalance().toPlainString() + "_"
                + ledger.getNonce() + "_"
                + ledger.getLastHash() + "_"
                + ledger.getContext() + "_"
                + System.currentTimeMillis();
        return hashPort.hash(data.toCharArray());
    }

    public String generateBalanceSignature(LedgerEntity ledger) {
        String payload = "BALANCE_SIG:"
                + ledger.getWallet().getUser().getId() + ":"
                + ledger.getWallet().getId() + ":"
                + ledger.getNonce() + ":"
                + ledger.getBalance().toPlainString();
        return hashPort.hash(payload.toCharArray());
    }
}
