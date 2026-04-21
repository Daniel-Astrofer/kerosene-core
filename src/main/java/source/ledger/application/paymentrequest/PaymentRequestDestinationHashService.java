package source.ledger.application.paymentrequest;

import org.springframework.stereotype.Service;
import source.wallet.model.WalletEntity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class PaymentRequestDestinationHashService {

    public String buildDestinationHash(WalletEntity wallet) {
        String source = firstNonBlank(
                wallet.getDepositAddress(),
                wallet.getPassphraseHash(),
                wallet.getId() == null ? null : "wallet:" + wallet.getId());

        if (source == null) {
            source = "wallet:unknown";
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value & 0xff));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to calculate payment destination hash", exception);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
