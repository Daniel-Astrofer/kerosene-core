package source.bitcoinaccounts.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.bitcoinaccounts.model.BitcoinAccountEntity;
import source.bitcoinaccounts.model.BitcoinAccountEnums;
import source.bitcoinaccounts.model.ColdWalletAddressEntity;
import source.bitcoinaccounts.model.ColdWalletEntity;
import source.bitcoinaccounts.model.ColdWalletUtxoEntity;
import source.bitcoinaccounts.repository.BitcoinAccountRepository;
import source.bitcoinaccounts.repository.ColdWalletAddressRepository;
import source.bitcoinaccounts.repository.ColdWalletRepository;
import source.bitcoinaccounts.repository.ColdWalletUtxoRepository;
import source.common.infra.logging.LogSanitizer;
import source.transactions.infra.BlockchainClient;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@ConditionalOnProperty(name = "kfe.legacy-financial.enabled", havingValue = "true")
public class ColdWalletMonitorService {

    private static final Logger log = LoggerFactory.getLogger(ColdWalletMonitorService.class);

    private final ColdWalletRepository coldWalletRepository;
    private final ColdWalletAddressRepository addressRepository;
    private final ColdWalletUtxoRepository utxoRepository;
    private final BitcoinAccountRepository accountRepository;
    private final BitcoinTaxEventService taxEventService;
    private final BlockchainClient blockchainClient;

    public ColdWalletMonitorService(
            ColdWalletRepository coldWalletRepository,
            ColdWalletAddressRepository addressRepository,
            ColdWalletUtxoRepository utxoRepository,
            BitcoinAccountRepository accountRepository,
            BitcoinTaxEventService taxEventService,
            BlockchainClient blockchainClient) {
        this.coldWalletRepository = coldWalletRepository;
        this.addressRepository = addressRepository;
        this.utxoRepository = utxoRepository;
        this.accountRepository = accountRepository;
        this.taxEventService = taxEventService;
        this.blockchainClient = blockchainClient;
    }

    @Scheduled(fixedDelayString = "${bitcoin-accounts.cold-wallet-monitor.fixed-delay-ms:60000}")
    public void scanColdWallets() {
        for (ColdWalletEntity wallet : coldWalletRepository.findTop100ByOrderByUpdatedAtAsc()) {
            try {
                scanWallet(wallet);
            } catch (Exception ex) {
                log.warn("[ColdWalletMonitor] Scan failed for coldWalletRef={}: {}",
                        LogSanitizer.fingerprint(wallet.getId().toString()),
                        ex.getMessage());
            }
        }
    }

    @Transactional
    public void scanWallet(ColdWalletEntity wallet) {
        BitcoinAccountEntity account = accountRepository.findById(wallet.getAccountId()).orElse(null);
        List<ColdWalletAddressEntity> addresses = addressRepository.findByColdWalletId(wallet.getId());
        Set<String> liveOutpoints = new HashSet<>();
        long observedBalance = 0L;

        for (ColdWalletAddressEntity address : addresses) {
            long addressBalance = 0L;
            for (BlockchainClient.AddressUtxo utxo : blockchainClient.getUnspentOutputs(address.getAddress())) {
                String outpoint = outpoint(utxo.txid(), utxo.vout());
                liveOutpoints.add(outpoint);
                addressBalance += Math.max(0L, utxo.valueSats());
                upsertUtxo(wallet, account, utxo);
            }
            address.setObservedBalanceSats(addressBalance);
            if (addressBalance > 0L) {
                address.setLastSeenAt(LocalDateTime.now());
            }
            addressRepository.save(address);
            observedBalance += addressBalance;
        }

        markSpentUtxos(wallet, account, liveOutpoints);
        wallet.setObservedBalanceSats(observedBalance);
        long height = currentBlockHeight();
        if (height > 0L) {
            wallet.setLastScanHeight(height);
        }
        coldWalletRepository.save(wallet);
    }

    private void upsertUtxo(
            ColdWalletEntity wallet,
            BitcoinAccountEntity account,
            BlockchainClient.AddressUtxo observed) {
        ColdWalletUtxoEntity utxo = utxoRepository.findByTxidAndVout(observed.txid(), observed.vout())
                .orElse(null);
        boolean isNew = false;
        if (utxo == null) {
            utxo = new ColdWalletUtxoEntity();
            utxo.setColdWalletId(wallet.getId());
            utxo.setTxid(observed.txid());
            utxo.setVout(observed.vout());
            isNew = true;
        }
        if (!wallet.getId().equals(utxo.getColdWalletId())) {
            return;
        }
        utxo.setAmountSats(Math.max(0L, observed.valueSats()));
        utxo.setConfirmations(confirmations(observed.txid()));
        if (utxo.getStatus() != BitcoinAccountEnums.UtxoStatus.LOCKED) {
            utxo.setStatus(BitcoinAccountEnums.UtxoStatus.UNSPENT);
        }
        utxoRepository.save(utxo);

        if (isNew && account != null) {
            taxEventService.recordTemporaryEvent(
                    account.getUserId(),
                    BitcoinAccountEnums.TaxEventType.COLD_WALLET_OBSERVED_IN,
                    observed.valueSats(),
                    outpoint(observed.txid(), observed.vout()),
                    account.getId(),
                    null,
                    wallet.getId(),
                    "OBSERVED_EXTERNAL_SELF_CUSTODY");
        }
    }

    private void markSpentUtxos(
            ColdWalletEntity wallet,
            BitcoinAccountEntity account,
            Set<String> liveOutpoints) {
        for (ColdWalletUtxoEntity existing : utxoRepository.findByColdWalletIdAndStatusIn(
                wallet.getId(),
                List.of(BitcoinAccountEnums.UtxoStatus.UNSPENT, BitcoinAccountEnums.UtxoStatus.LOCKED))) {
            if (liveOutpoints.contains(outpoint(existing.getTxid(), existing.getVout()))) {
                continue;
            }
            existing.setStatus(BitcoinAccountEnums.UtxoStatus.SPENT);
            utxoRepository.save(existing);
            if (account != null) {
                taxEventService.recordTemporaryEvent(
                        account.getUserId(),
                        BitcoinAccountEnums.TaxEventType.COLD_WALLET_OBSERVED_OUT,
                        existing.getAmountSats(),
                        outpoint(existing.getTxid(), existing.getVout()),
                        account.getId(),
                        null,
                        wallet.getId(),
                        "OBSERVED_EXTERNAL_SELF_CUSTODY");
            }
        }
    }

    private int confirmations(String txid) {
        JsonNode transaction = blockchainClient.getRawTransaction(txid, true);
        if (transaction == null || transaction.isNull() || transaction.isMissingNode()) {
            return 0;
        }
        JsonNode confirmations = transaction.path("confirmations");
        return confirmations.isNumber() ? Math.max(0, confirmations.asInt()) : 0;
    }

    private long currentBlockHeight() {
        JsonNode node = blockchainClient.executeRpc("getblockcount");
        if (node != null && node.has("result")) {
            node = node.get("result");
        }
        return node != null && node.isNumber() ? Math.max(0L, node.asLong()) : 0L;
    }

    private String outpoint(String txid, int vout) {
        return txid + ":" + vout;
    }
}
