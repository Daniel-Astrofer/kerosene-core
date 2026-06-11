package source.kfe.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.common.service.AddressDerivationService;
import source.kfe.dto.KfeAddressResponse;
import source.kfe.dto.KfeCreateWalletRequest;
import source.kfe.dto.KfeWalletResponse;
import source.kfe.model.KfeWalletAddressEntity;
import source.kfe.model.KfeWalletAddressRole;
import source.kfe.model.KfeWalletAddressStatus;
import source.kfe.model.KfeWalletEntity;
import source.kfe.model.KfeWalletKind;
import source.kfe.model.KfeWalletStatus;
import source.kfe.repository.KfeWalletAddressRepository;
import source.kfe.repository.KfeWalletRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KfeWalletService {

    private static final String ASSET_BTC = "BTC";

    private final KfeWalletRepository walletRepository;
    private final KfeWalletAddressRepository addressRepository;
    private final KfeBalanceService balanceService;
    private final KfeHashService hashService;
    private final KfeAuditLogService auditLogService;
    private final KfeQuorumGateway quorumGateway;
    private final KfeMpcKeyService mpcKeyService;
    private final KfeResponseMapper responseMapper;
    private final KfeDashboardPublisher dashboardPublisher;
    private final AddressDerivationService addressDerivationService;

    public KfeWalletService(
            KfeWalletRepository walletRepository,
            KfeWalletAddressRepository addressRepository,
            KfeBalanceService balanceService,
            KfeHashService hashService,
            KfeAuditLogService auditLogService,
            KfeQuorumGateway quorumGateway,
            KfeMpcKeyService mpcKeyService,
            KfeResponseMapper responseMapper,
            KfeDashboardPublisher dashboardPublisher,
            AddressDerivationService addressDerivationService) {
        this.walletRepository = walletRepository;
        this.addressRepository = addressRepository;
        this.balanceService = balanceService;
        this.hashService = hashService;
        this.auditLogService = auditLogService;
        this.quorumGateway = quorumGateway;
        this.mpcKeyService = mpcKeyService;
        this.responseMapper = responseMapper;
        this.dashboardPublisher = dashboardPublisher;
        this.addressDerivationService = addressDerivationService;
    }

    @Transactional
    public KfeWalletResponse createWallet(Long userId, KfeCreateWalletRequest request) {
        validateCreateRequest(request);

        KfeWalletEntity wallet = new KfeWalletEntity();
        wallet.setUserId(userId);
        wallet.setKind(request.kind());
        wallet.setStatus(KfeWalletStatus.CREATING);
        wallet.setLabel(cleanLabel(request.label()));
        wallet.setAsset(ASSET_BTC);
        wallet.setSpendable(request.kind() != KfeWalletKind.WATCH_ONLY);
        wallet.setXpub(blankToNull(request.xpub()));
        wallet.setDescriptor(blankToNull(request.descriptor()));
        wallet.setFingerprint(blankToNull(request.fingerprint()));
        wallet.setDerivationPath(blankToNull(request.derivationPath()));
        wallet.setQuorumPolicyHash(quorumPolicyHash(request.kind()));
        wallet = walletRepository.save(wallet);
        balanceService.createEmptyBalance(wallet.getId(), wallet.getAsset());

        String proposalHash = hashService.sha256("KFE_WALLET_CREATE|" + userId + "|" + wallet.getId()
                + "|" + wallet.getKind() + "|" + wallet.getQuorumPolicyHash());
        KfeQuorumGateway.Result quorum = quorumGateway.requireHealthyUnanimousConsensus(proposalHash);

        if (wallet.getKind() == KfeWalletKind.CUSTODIAL_ONCHAIN) {
            wallet.setMpcPublicKey(mpcKeyService.keygenWallet(wallet.getId(), userId));
        }
        wallet.setStatus(KfeWalletStatus.ACTIVE);
        wallet = walletRepository.save(wallet);

        if (hasText(request.initialAddress())) {
            createProvidedAddress(wallet, request.initialAddress());
        } else if (Boolean.TRUE.equals(request.issueInitialAddress())) {
            issueFreshAddress(wallet, false);
        }

        auditLogService.record(
                "KFE_WALLET_CREATED",
                null,
                wallet.getId(),
                null,
                null,
                Map.of(
                        "walletId", wallet.getId().toString(),
                        "kind", wallet.getKind().name(),
                        "proposalHash", proposalHash,
                        "quorumAckCount", quorum.acceptedNodes()));
        dashboardPublisher.publishAfterCommit(userId);
        return responseMapper.toWalletResponse(wallet);
    }

    @Transactional(readOnly = true)
    public List<KfeWalletResponse> listWallets(Long userId) {
        return walletRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(responseMapper::toWalletResponse)
                .toList();
    }

    @Transactional
    public KfeAddressResponse rotateAddress(Long userId, UUID walletId) {
        KfeWalletEntity wallet = walletRepository.findByIdAndUserIdForUpdate(walletId, userId)
                .orElseThrow(() -> new IllegalArgumentException("KFE wallet not found."));
        if (wallet.getKind() == KfeWalletKind.WATCH_ONLY) {
            throw new IllegalArgumentException("WATCH_ONLY wallets do not issue receiving addresses.");
        }
        requireActive(wallet);

        wallet.setStatus(KfeWalletStatus.ROTATING_ADDRESS);
        walletRepository.save(wallet);
        String proposalHash = hashService.sha256("KFE_WALLET_ADDRESS_ROTATE|" + userId + "|" + wallet.getId());
        KfeQuorumGateway.Result quorum = quorumGateway.requireHealthyUnanimousConsensus(proposalHash);

        KfeWalletAddressEntity address = issueFreshAddress(wallet, true);
        wallet.setStatus(KfeWalletStatus.ACTIVE);
        walletRepository.save(wallet);

        auditLogService.record(
                "KFE_WALLET_ADDRESS_ROTATED",
                null,
                wallet.getId(),
                null,
                null,
                Map.of(
                        "walletId", wallet.getId().toString(),
                        "addressId", address.getId().toString(),
                        "proposalHash", proposalHash,
                        "quorumAckCount", quorum.acceptedNodes()));
        dashboardPublisher.publishAfterCommit(userId);
        return responseMapper.toAddressResponse(address);
    }

    private void validateCreateRequest(KfeCreateWalletRequest request) {
        if (request.kind() == KfeWalletKind.WATCH_ONLY
                && !hasText(request.xpub())
                && !hasText(request.descriptor())) {
            throw new IllegalArgumentException("WATCH_ONLY wallets require xpub or descriptor.");
        }
        if (request.kind() == KfeWalletKind.CUSTODIAL_ONCHAIN
                && !hasText(request.xpub())) {
            throw new IllegalArgumentException(
                    "CUSTODIAL_ONCHAIN wallets require an XPUB until MPC sidecar exposes native XPUB generation.");
        }
    }

    private KfeWalletAddressEntity issueFreshAddress(KfeWalletEntity wallet, boolean retireExisting) {
        if (retireExisting) {
            addressRepository.findByWalletIdAndStatusOrderByCreatedAtDesc(
                            wallet.getId(),
                            KfeWalletAddressStatus.ACTIVE)
                    .forEach(address -> {
                        address.retire();
                        addressRepository.save(address);
                    });
        }

        if (hasText(wallet.getXpub())) {
            int nextIndex = wallet.getLastDerivedIndex() + 1;
            AddressDerivationService.DerivedAddress derived =
                    addressDerivationService.deriveAddressDetailsFromXpub(wallet.getXpub(), nextIndex);
            wallet.setLastDerivedIndex(nextIndex);
            walletRepository.save(wallet);
            return saveAddress(
                    wallet,
                    derived.address(),
                    "m/84'/0'/0'/0/" + nextIndex,
                    nextIndex,
                    "KFE_XPUB_DERIVATION");
        }

        throw new IllegalStateException("KFE address rotation requires wallet XPUB or an initial address.");
    }

    private KfeWalletAddressEntity createProvidedAddress(KfeWalletEntity wallet, String addressValue) {
        return saveAddress(wallet, addressValue.trim(), null, null, "CLIENT_PROVIDED");
    }

    private KfeWalletAddressEntity saveAddress(
            KfeWalletEntity wallet,
            String addressValue,
            String derivationPath,
            Integer derivationIndex,
            String providerReference) {
        KfeWalletAddressEntity address = new KfeWalletAddressEntity();
        address.setWalletId(wallet.getId());
        address.setAddress(addressValue);
        address.setAddressRole(KfeWalletAddressRole.RECEIVE);
        address.setStatus(KfeWalletAddressStatus.ACTIVE);
        address.setDerivationPath(derivationPath);
        address.setDerivationIndex(derivationIndex);
        address.setProviderReference(providerReference);
        return addressRepository.save(address);
    }

    private void requireActive(KfeWalletEntity wallet) {
        if (wallet.getStatus() != KfeWalletStatus.ACTIVE) {
            throw new IllegalStateException("Wallet is not active.");
        }
    }

    private String quorumPolicyHash(KfeWalletKind kind) {
        return hashService.sha256("KFE_WALLET_POLICY|kind=" + kind
                + "|quorum=healthy-unanimous-min-2|pricing=onchain-0.9pct");
    }

    private String cleanLabel(String label) {
        String clean = label != null ? label.trim() : "";
        if (clean.isBlank()) {
            throw new IllegalArgumentException("Wallet label is required.");
        }
        return clean.length() > 96 ? clean.substring(0, 96) : clean;
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
