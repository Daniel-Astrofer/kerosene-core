package source.ledger.application.paymentrequest;

import org.springframework.stereotype.Service;
import source.wallet.domain.WalletDestinationHash;
import source.wallet.model.WalletEntity;

@Service
public class PaymentRequestDestinationHashService {

    public String buildDestinationHash(WalletEntity wallet) {
        return WalletDestinationHash.fromParts(
                wallet.getDepositAddress(),
                wallet.getPassphraseHash(),
                wallet.getId());
    }
}
