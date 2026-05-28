package source.bitcoinaccounts.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.common.infra.logging.LogSanitizer;
import source.common.service.AddressDerivationService;
import source.transactions.infra.BitcoinCoreRpcClient;
import source.transactions.service.CustodialDerivationCursorService;
import source.transactions.service.WatchOnlyAddressImportPort;

@Service
public class BitcoinReceiveAddressIssuer {

    private final AddressDerivationService addressDerivationService;
    private final CustodialDerivationCursorService cursorService;
    private final WatchOnlyAddressImportPort watchOnlyAddressImportPort;
    private final BitcoinCoreRpcClient bitcoinCoreRpcClient;
    private final String platformMasterXpub;
    private final boolean bitcoinCoreWalletAddressEnabled;

    public BitcoinReceiveAddressIssuer(
            AddressDerivationService addressDerivationService,
            CustodialDerivationCursorService cursorService,
            WatchOnlyAddressImportPort watchOnlyAddressImportPort,
            ObjectProvider<BitcoinCoreRpcClient> bitcoinCoreRpcClient,
            @Value("${bitcoin.platform.master-xpub:${bitcoin.hot-wallet.xpub:}}") String platformMasterXpub,
            @Value("${transactions.bitcoin-core-wallet-address-enabled:false}") boolean bitcoinCoreWalletAddressEnabled) {
        this.addressDerivationService = addressDerivationService;
        this.cursorService = cursorService;
        this.watchOnlyAddressImportPort = watchOnlyAddressImportPort;
        this.bitcoinCoreRpcClient = bitcoinCoreRpcClient.getIfAvailable();
        this.platformMasterXpub = platformMasterXpub != null ? platformMasterXpub.trim() : "";
        this.bitcoinCoreWalletAddressEnabled = bitcoinCoreWalletAddressEnabled;
    }

    public IssuedAddress issue(String label) {
        if (!platformMasterXpub.isBlank()) {
            int index = cursorService.nextIndex(CustodialDerivationCursorService.KEROSENE_BIP84_EXTERNAL);
            AddressDerivationService.DerivedAddress derived =
                    addressDerivationService.deriveAddressDetailsFromXpub(platformMasterXpub, index);
            watchOnlyAddressImportPort.importWatchOnlyPublicKey(derived.publicKey(), derived.address());
            return new IssuedAddress(
                    derived.address(),
                    "m/84'/0'/0'/0/" + derived.index(),
                    derived.index(),
                    "PLATFORM_XPUB:" + LogSanitizer.fingerprint(platformMasterXpub));
        }
        if (bitcoinCoreWalletAddressEnabled && bitcoinCoreRpcClient != null) {
            String address = bitcoinCoreRpcClient.getNewAddress(label != null ? label : "kerosene-receive");
            return new IssuedAddress(
                    address,
                    "bitcoin-core-wallet:" + bitcoinCoreRpcClient.walletName(),
                    -1,
                    "BITCOIN_CORE_WALLET");
        }
        throw new IllegalStateException(
                "On-chain receive links require bitcoin.platform.master-xpub or Bitcoin Core wallet address issuance.");
    }

    public record IssuedAddress(
            String address,
            String derivationPath,
            int derivationIndex,
            String providerReference) {
    }
}
