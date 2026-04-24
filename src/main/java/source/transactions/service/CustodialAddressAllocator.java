package source.transactions.service;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.common.service.AddressDerivationService;
import source.ledger.sync.QuorumSyncService;
import source.transactions.exception.ExternalPaymentsExceptions;
import source.wallet.exceptions.WalletExceptions;
import source.wallet.model.WalletEntity;
import source.wallet.repository.WalletRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class CustodialAddressAllocator {

    private static final Logger log = LoggerFactory.getLogger(CustodialAddressAllocator.class);

    private final WalletRepository walletRepository;
    private final AddressDerivationService addressDerivationService;
    private final QuorumSyncService quorumSyncService;
    private final CustodialDerivationCursorService custodialDerivationCursorService;
    private final BitcoinNodeService bitcoinNodeService;
    private final String platformMasterXpub;
    private final String localAddressProviderName;

    public CustodialAddressAllocator(
            WalletRepository walletRepository,
            AddressDerivationService addressDerivationService,
            QuorumSyncService quorumSyncService,
            CustodialDerivationCursorService custodialDerivationCursorService,
            BitcoinNodeService bitcoinNodeService,
            @Value("${bitcoin.platform.master-xpub:}") String platformMasterXpub,
            @Value("${bitcoin.hot-wallet.xpub:}") String hotWalletXpub,
            @Value("${transactions.local-address-provider-name:KEROSENE_LOCAL}") String localAddressProviderName) {
        this.walletRepository = walletRepository;
        this.addressDerivationService = addressDerivationService;
        this.quorumSyncService = quorumSyncService;
        this.custodialDerivationCursorService = custodialDerivationCursorService;
        this.bitcoinNodeService = bitcoinNodeService;
        this.platformMasterXpub = firstNonBlank(platformMasterXpub, hotWalletXpub);
        this.localAddressProviderName = localAddressProviderName;
    }

    @Transactional
    public Allocation allocate(Long userId, WalletEntity wallet, String label, boolean forceFresh) {
        WalletEntity lockedWallet = walletRepository.findByIdForUpdate(wallet.getId())
                .orElseThrow(() -> new WalletExceptions.WalletNoExists("wallet not found"));

        String sourceXpub;
        int derivationIndex;
        String externalReference;
        String provider = resolveProviderName(lockedWallet);

        if (lockedWallet.isSelfCustodyMode()) {
            if (!hasWalletXpub(lockedWallet)) {
                throw new ExternalPaymentsExceptions.CustodyProviderUnavailable(
                        "Self-custody wallets require a registered XPUB before issuing a deposit address.");
            }
            sourceXpub = lockedWallet.getXpub();
            derivationIndex = nextWalletDerivedIndex(lockedWallet);
            externalReference = "SELF_CUSTODY_BIP84_EXTERNAL_" + derivationIndex;
        } else if (platformMasterXpub != null && !platformMasterXpub.isBlank()) {
            sourceXpub = platformMasterXpub;
            derivationIndex = custodialDerivationCursorService.nextIndex(CustodialDerivationCursorService.KEROSENE_BIP84_EXTERNAL);
            externalReference = "KEROSENE_QUORUM_BIP84_EXTERNAL_" + derivationIndex;
        } else {
            throw new ExternalPaymentsExceptions.CustodyProviderUnavailable(
                    "KEROSENE on-chain address issuance requires bitcoin.platform.master-xpub or a live custody provider.");
        }

        AddressDerivationService.DerivedAddress derivedAddress = addressDerivationService
                .deriveAddressDetailsFromXpub(sourceXpub, derivationIndex);
        bitcoinNodeService.importWatchOnlyPublicKey(derivedAddress.publicKey(), derivedAddress.address());

        if (lockedWallet.isKeroseneCustodyMode()) {
            assertQuorum(lockedWallet, label, derivedAddress.address(), externalReference);
        }

        lockedWallet.setDepositAddress(derivedAddress.address());
        lockedWallet.setExternalWalletReference(externalReference);
        if (lockedWallet.isSelfCustodyMode()) {
            lockedWallet.setLastDerivedIndex(derivationIndex);
        }
        walletRepository.save(lockedWallet);

        log.info("[CustodyAddress] Issued fresh on-chain address {} at index {} for wallet {}.",
                derivedAddress.address(),
                derivationIndex,
                lockedWallet.getId());
        return new Allocation(derivedAddress.address(), externalReference, provider, false);
    }

    private int nextWalletDerivedIndex(WalletEntity wallet) {
        Integer currentIndex = wallet.getLastDerivedIndex();
        int nextIndex = currentIndex == null ? 0 : currentIndex + 1;
        wallet.setLastDerivedIndex(nextIndex);
        return nextIndex;
    }

    private boolean hasWalletXpub(WalletEntity wallet) {
        return wallet.getXpub() != null && !wallet.getXpub().isBlank();
    }

    private String resolveProviderName(WalletEntity wallet) {
        if (wallet != null && wallet.isSelfCustodyMode()) {
            return "SELF_CUSTODY_XPUB";
        }
        return firstNonBlank(bitcoinNodeService.providerName(), localAddressProviderName);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void assertQuorum(WalletEntity wallet, String label, String address, String externalReference) {
        String proposalHash = sha256Hex(
                "CUSTODIAL_ADDRESS|" + wallet.getId() + "|" + safe(label) + "|" + safe(address) + "|" + safe(externalReference));
        boolean quorumAccepted = quorumSyncService.proposeTransactionToQuorum(proposalHash);
        if (!quorumAccepted) {
            throw new IllegalStateException("Failed to reach architecture quorum while allocating the custodial address.");
        }
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable for quorum hashing", e);
        }
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    public record Allocation(
            String address,
            String externalReference,
            String provider,
            boolean reused) {
    }
}
