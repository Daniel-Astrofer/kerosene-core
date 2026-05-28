package source.bitcoinaccounts.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.bitcoinaccounts.model.BitcoinAccountEntity;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.ColdWalletAddressEntity;
import source.bitcoinaccounts.model.ColdWalletEntity;
import source.bitcoinaccounts.model.InternalBtcCardEntity;
import source.bitcoinaccounts.model.LedgerAccountEntity;
import source.bitcoinaccounts.repository.BitcoinAccountRepository;
import source.bitcoinaccounts.repository.ColdWalletAddressRepository;
import source.bitcoinaccounts.repository.ColdWalletRepository;
import source.bitcoinaccounts.repository.InternalBtcCardRepository;
import source.common.service.AddressDerivationService;
import source.transactions.infra.BitcoinCoreRpcClient;

import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BitcoinAccountService {

    private static final long DEFAULT_DAILY_LIMIT_SATS = 5_000_000L;
    private static final long DEFAULT_MONTHLY_LIMIT_SATS = 50_000_000L;

    private final BitcoinAccountRepository accountRepository;
    private final InternalBtcCardRepository cardRepository;
    private final ColdWalletRepository coldWalletRepository;
    private final ColdWalletAddressRepository coldWalletAddressRepository;
    private final BitcoinAccountLedgerService ledgerService;
    private final BitcoinAccountSecurityService securityService;
    private final BitcoinAccountAuditService auditService;
    private final AddressDerivationService addressDerivationService;
    private final BitcoinCoreRpcClient bitcoinCoreRpcClient;

    public BitcoinAccountService(
            BitcoinAccountRepository accountRepository,
            InternalBtcCardRepository cardRepository,
            ColdWalletRepository coldWalletRepository,
            ColdWalletAddressRepository coldWalletAddressRepository,
            BitcoinAccountLedgerService ledgerService,
            BitcoinAccountSecurityService securityService,
            BitcoinAccountAuditService auditService,
            AddressDerivationService addressDerivationService,
            ObjectProvider<BitcoinCoreRpcClient> bitcoinCoreRpcClient) {
        this.accountRepository = accountRepository;
        this.cardRepository = cardRepository;
        this.coldWalletRepository = coldWalletRepository;
        this.coldWalletAddressRepository = coldWalletAddressRepository;
        this.ledgerService = ledgerService;
        this.securityService = securityService;
        this.auditService = auditService;
        this.addressDerivationService = addressDerivationService;
        this.bitcoinCoreRpcClient = bitcoinCoreRpcClient.getIfAvailable();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long userId) {
        return accountRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toAccountView)
                .toList();
    }

    @Transactional
    public Map<String, Object> createInternalCard(Long userId, String label, String riskTier) {
        BitcoinAccountEntity account = new BitcoinAccountEntity();
        account.setUserId(userId);
        account.setType(BitcoinAccountEnums.AccountType.INTERNAL_CARD);
        account.setCustody(BitcoinAccountEnums.CustodyType.KEROSENE_CUSTODIAL);
        account.setStatus(BitcoinAccountEnums.AccountStatus.ACTIVE);
        account.setLabel(cleanLabel(label, "Internal BTC Card"));
        account.setRiskTier(cleanRiskTier(riskTier));
        account = accountRepository.save(account);

        LedgerAccountEntity ledger = ledgerService.createLedgerAccount(userId, account.getId());

        InternalBtcCardEntity card = new InternalBtcCardEntity();
        card.setBitcoinAccountId(account.getId());
        card.setLedgerAccountId(ledger.getId());
        card.setDailyLimitSats(DEFAULT_DAILY_LIMIT_SATS);
        card.setMonthlyLimitSats(DEFAULT_MONTHLY_LIMIT_SATS);
        card = cardRepository.save(card);

        auditService.recordUser(userId, "INTERNAL_BTC_CARD_CREATED", "INTERNAL_BTC_CARD",
                card.getId().toString(), Map.of("bitcoinAccountId", account.getId().toString()));
        return toAccountView(account);
    }

    @Transactional
    public Map<String, Object> createColdWallet(
            Long userId,
            String label,
            String descriptor,
            String xpub,
            String fingerprint,
            String derivationPath,
        BitcoinAccountEnums.ScriptPolicy scriptPolicy) {
        securityService.validatePublicWatchOnlyMaterial(descriptor, xpub);
        securityService.validateColdWalletMetadata(fingerprint, derivationPath);

        BitcoinAccountEntity account = new BitcoinAccountEntity();
        account.setUserId(userId);
        account.setType(BitcoinAccountEnums.AccountType.WATCH_ONLY_COLD_WALLET);
        account.setCustody(BitcoinAccountEnums.CustodyType.WATCH_ONLY);
        account.setStatus(BitcoinAccountEnums.AccountStatus.ACTIVE);
        account.setLabel(cleanLabel(label, "Cold Wallet"));
        account.setRiskTier("WATCH_ONLY");
        account = accountRepository.save(account);

        ColdWalletEntity coldWallet = new ColdWalletEntity();
        coldWallet.setAccountId(account.getId());
        coldWallet.setDescriptor(blankToNull(descriptor));
        coldWallet.setXpub(blankToNull(xpub));
        coldWallet.setFingerprint(fingerprint.trim());
        coldWallet.setDerivationPath(derivationPath.trim());
        coldWallet.setScriptPolicy(scriptPolicy != null ? scriptPolicy : BitcoinAccountEnums.ScriptPolicy.SINGLE_SIG);
        coldWallet = coldWalletRepository.save(coldWallet);

        importDescriptorIfPossible(coldWallet);
        derivePreviewAddresses(coldWallet);

        auditService.recordUser(userId, "COLD_WALLET_IMPORTED", "COLD_WALLET",
                coldWallet.getId().toString(), Map.of("accountId", account.getId().toString(), "canSign", false));
        return toAccountView(account);
    }

    @Transactional(readOnly = true)
    public BitcoinAccountEntity requireOwnedAccount(Long userId, UUID accountId) {
        return accountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Bitcoin account not found."));
    }

    @Transactional(readOnly = true)
    public InternalBtcCardEntity requireInternalCard(Long userId, UUID accountId) {
        BitcoinAccountEntity account = requireOwnedAccount(userId, accountId);
        if (account.getType() != BitcoinAccountEnums.AccountType.INTERNAL_CARD) {
            throw new IllegalArgumentException("This operation requires an Internal BTC Card.");
        }
        if (account.getStatus() != BitcoinAccountEnums.AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("This Bitcoin account is not active.");
        }
        return cardRepository.findByBitcoinAccountId(account.getId())
                .orElseThrow(() -> new IllegalArgumentException("Internal BTC Card not found."));
    }

    @Transactional(readOnly = true)
    public ColdWalletEntity requireOwnedColdWallet(Long userId, UUID coldWalletId) {
        ColdWalletEntity wallet = coldWalletRepository.findById(coldWalletId)
                .orElseThrow(() -> new IllegalArgumentException("Cold wallet not found."));
        BitcoinAccountEntity account = requireOwnedAccount(userId, wallet.getAccountId());
        if (account.getType() != BitcoinAccountEnums.AccountType.WATCH_ONLY_COLD_WALLET) {
            throw new IllegalArgumentException("This account is not a watch-only cold wallet.");
        }
        return wallet;
    }

    private Map<String, Object> toAccountView(BitcoinAccountEntity account) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", account.getId());
        view.put("type", account.getType());
        view.put("custody", account.getCustody());
        view.put("status", account.getStatus());
        view.put("label", account.getLabel());
        view.put("riskTier", account.getRiskTier());
        view.put("createdAt", account.getCreatedAt());

        if (account.getType() == BitcoinAccountEnums.AccountType.INTERNAL_CARD) {
            cardRepository.findByBitcoinAccountId(account.getId()).ifPresent(card -> {
                view.put("cardId", card.getId());
                view.put("ledgerAccountId", card.getLedgerAccountId());
                view.put("dailyLimitSats", card.getDailyLimitSats());
                view.put("monthlyLimitSats", card.getMonthlyLimitSats());
                view.put("cardStatus", card.getStatus());
                LedgerAccountEntity ledger = ledgerService.getBalances(card.getLedgerAccountId());
                view.put("balanceAvailableSats", ledger.getBalanceAvailableSats());
                view.put("balancePendingSats", ledger.getBalancePendingSats());
                view.put("balanceLockedSats", ledger.getBalanceLockedSats());
                view.put("balanceAutoHoldSats", ledger.getBalanceAutoHoldSats());
            });
        }
        if (account.getType() == BitcoinAccountEnums.AccountType.WATCH_ONLY_COLD_WALLET) {
            coldWalletRepository.findByAccountId(account.getId()).ifPresent(wallet -> {
                view.put("coldWalletId", wallet.getId());
                view.put("observedBalanceSats", wallet.getObservedBalanceSats());
                view.put("scriptPolicy", wallet.getScriptPolicy());
                view.put("canSign", false);
                view.put("derivationPath", wallet.getDerivationPath());
                view.put("fingerprint", wallet.getFingerprint());
            });
        }
        return view;
    }

    private void derivePreviewAddresses(ColdWalletEntity wallet) {
        if (wallet.getXpub() == null || wallet.getXpub().isBlank()) {
            return;
        }
        for (int i = 0; i < 5; i++) {
            ColdWalletAddressEntity address = new ColdWalletAddressEntity();
            address.setColdWalletId(wallet.getId());
            address.setDerivationIndex(i);
            address.setChange(false);
            address.setAddress(addressDerivationService.deriveAddressFromXpub(wallet.getXpub(), i, false));
            coldWalletAddressRepository.save(address);
        }
    }

    private void importDescriptorIfPossible(ColdWalletEntity wallet) {
        if (bitcoinCoreRpcClient == null || wallet.getDescriptor() == null || wallet.getDescriptor().isBlank()) {
            return;
        }
        bitcoinCoreRpcClient.importWatchOnlyDescriptor(wallet.getDescriptor(), LocalDateTime.now().minusDays(1));
    }

    private String cleanLabel(String value, String fallback) {
        String clean = value != null ? value.trim() : "";
        if (clean.isEmpty()) {
            return fallback;
        }
        return clean.length() > 96 ? clean.substring(0, 96) : clean;
    }

    private String cleanRiskTier(String value) {
        String clean = value != null ? value.trim().toUpperCase(java.util.Locale.ROOT) : "";
        return switch (clean) {
            case "GOLD", "BLACK" -> clean;
            default -> "BRONZE";
        };
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
