package source.transactions.service;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import source.common.service.AddressDerivationService;
import source.ledger.sync.QuorumSyncService;
import source.transactions.infra.CustodyGateway;
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
    private final CustodyGateway custodyGateway;
    private final String platformMasterXpub;
    private final String localAddressProviderName;

    public CustodialAddressAllocator(
            WalletRepository walletRepository,
            AddressDerivationService addressDerivationService,
            QuorumSyncService quorumSyncService,
            CustodyGateway custodyGateway,
            @Value("${bitcoin.platform.master-xpub:}") String platformMasterXpub,
            @Value("${bitcoin.hot-wallet.xpub:}") String hotWalletXpub,
            @Value("${transactions.local-address-provider-name:KEROSENE_LOCAL}") String localAddressProviderName) {
        this.walletRepository = walletRepository;
        this.addressDerivationService = addressDerivationService;
        this.quorumSyncService = quorumSyncService;
        this.custodyGateway = custodyGateway;
        this.platformMasterXpub = firstNonBlank(platformMasterXpub, hotWalletXpub);
        this.localAddressProviderName = localAddressProviderName;
    }

    @Transactional
    public Allocation allocate(Long userId, WalletEntity wallet, String label, boolean forceFresh) {
        WalletEntity lockedWallet = walletRepository.findByIdForUpdate(wallet.getId())
                .orElseThrow(() -> new WalletExceptions.WalletNoExists("wallet not found"));

        ensureCustodialWalletBranch(lockedWallet);

        if (!forceFresh && hasCurrentAddress(lockedWallet)) {
            return new Allocation(
                    lockedWallet.getDepositAddress(),
                    firstNonBlank(lockedWallet.getExternalWalletReference(), "CURRENT_ADDRESS"),
                    resolveProviderName(),
                    true);
        }

        String address;
        String externalReference;
        String provider = resolveProviderName();

        if (custodyGateway.isLive()) {
            CustodyGateway.GeneratedOnchainAddress issued = custodyGateway.createOnchainAddress(
                    new CustodyGateway.OnchainAddressCommand(
                            userId,
                            lockedWallet.getId(),
                            lockedWallet.getName(),
                            label));
            address = issued.address();
            externalReference = firstNonBlank(
                    issued.walletReference(),
                    issued.providerReference(),
                    "CUSTODY_ADDRESS");
        } else if (hasWalletXpub(lockedWallet)) {
            int nextIndex = nextDerivedIndex(lockedWallet);
            address = addressDerivationService.deriveAddressFromXpub(lockedWallet.getXpub(), nextIndex);
            externalReference = "XPUB_INDEX_" + nextIndex;
            provider = localAddressProviderName;
        } else {
            address = lockedWallet.getDepositAddress();
            if (address == null || address.isBlank()) {
                address = addressDerivationService.deriveAddress(lockedWallet.getId(), lockedWallet.getPassphraseHash());
            }
            externalReference = "STATIC_DERIVATION";
            provider = localAddressProviderName;
        }

        assertQuorum(lockedWallet, label, address, externalReference);

        lockedWallet.setDepositAddress(address);
        lockedWallet.setExternalWalletReference(externalReference);
        walletRepository.save(lockedWallet);

        return new Allocation(address, externalReference, provider, false);
    }

    private void ensureCustodialWalletBranch(WalletEntity wallet) {
        if (hasWalletXpub(wallet)) {
            return;
        }

        if (platformMasterXpub == null || platformMasterXpub.isBlank()) {
            return;
        }

        int branchIndex = Math.toIntExact(wallet.getId());
        String walletScopedXpub = addressDerivationService.deriveChildXpub(platformMasterXpub, branchIndex);
        wallet.setXpub(walletScopedXpub);
        if (wallet.getLastDerivedIndex() == null || wallet.getLastDerivedIndex() < -1) {
            wallet.setLastDerivedIndex(-1);
        }
        log.info("[CustodyAddress] Derived wallet-scoped xpub from Kerosene master wallet for wallet {}.", wallet.getId());
    }

    private int nextDerivedIndex(WalletEntity wallet) {
        Integer currentIndex = wallet.getLastDerivedIndex();
        int nextIndex = currentIndex == null ? 0 : currentIndex + 1;
        wallet.setLastDerivedIndex(nextIndex);
        return nextIndex;
    }

    private boolean hasWalletXpub(WalletEntity wallet) {
        return wallet.getXpub() != null && !wallet.getXpub().isBlank();
    }

    private boolean hasCurrentAddress(WalletEntity wallet) {
        return wallet.getDepositAddress() != null && !wallet.getDepositAddress().isBlank();
    }

    private String resolveProviderName() {
        return firstNonBlank(custodyGateway.providerName(), localAddressProviderName);
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
