package source.transactions.application.externalpayments;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalPaymentsMathTest {

    @Test
    void validatesAddressChecksumAndConfiguredNetwork() {
        ECKey key = new ECKey();
        String mainnetAddress = SegwitAddress.fromHash(MainNetParams.get(), key.getPubKeyHash()).toString();
        String testnetAddress = SegwitAddress.fromHash(TestNet3Params.get(), key.getPubKeyHash()).toString();

        assertTrue(new ExternalPaymentsMath("mainnet").isValidBitcoinAddress(mainnetAddress));
        assertTrue(new ExternalPaymentsMath("testnet").isValidBitcoinAddress(testnetAddress));
        assertFalse(new ExternalPaymentsMath("mainnet").isValidBitcoinAddress(testnetAddress));
        assertFalse(new ExternalPaymentsMath("testnet").isValidBitcoinAddress(mainnetAddress));
    }

    @Test
    void rejectsRegexLookalikesWithInvalidChecksum() {
        ECKey key = new ECKey();
        String valid = SegwitAddress.fromHash(MainNetParams.get(), key.getPubKeyHash()).toString();
        String invalidChecksum = valid.substring(0, valid.length() - 1)
                + (valid.endsWith("q") ? "p" : "q");

        assertFalse(new ExternalPaymentsMath("mainnet").isValidBitcoinAddress(invalidChecksum));
        assertFalse(new ExternalPaymentsMath("mainnet").isValidBitcoinAddress("bc1qnotarealaddress000000000000000000"));
    }

    @Test
    void btcToSatsRequiresExactSatoshiPrecision() {
        ExternalPaymentsMath math = new ExternalPaymentsMath("mainnet");

        assertEquals(1L, math.btcToSats(new BigDecimal("0.00000001")));
        assertThrows(IllegalArgumentException.class, () -> math.btcToSats(new BigDecimal("0.000000001")));
    }
}
