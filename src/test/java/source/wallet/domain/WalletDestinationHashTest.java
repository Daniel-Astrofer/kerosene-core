package source.wallet.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WalletDestinationHashTest {

    @Test
    void usesDepositAddressBeforePassphraseHash() {
        String fromDeposit = WalletDestinationHash.fromParts("bc1qwallet", "passphrase-hash", 99L);
        String withoutPassphrase = WalletDestinationHash.fromParts("bc1qwallet", null, 99L);

        assertEquals(withoutPassphrase, fromDeposit);
    }

    @Test
    void normalizesDestinationHash() {
        assertEquals("abc123", WalletDestinationHash.normalize("  ABC123  "));
        assertNull(WalletDestinationHash.normalize("  "));
    }
}
