package source.transactions.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.transactions.model.BlockchainAddressWatchEntity;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.repository.BlockchainAddressWatchRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BlockchainAddressWatchService {

    private static final List<String> ACTIVE_STATUSES = List.of("WATCHING", "DETECTED", "CONFIRMED");

    private final BlockchainAddressWatchRepository repository;
    private final int minimumConfirmations;

    public BlockchainAddressWatchService(
            BlockchainAddressWatchRepository repository,
            @org.springframework.beans.factory.annotation.Value("${bitcoin.min-confirmations:3}") int minimumConfirmations) {
        this.repository = repository;
        this.minimumConfirmations = Math.max(1, minimumConfirmations);
    }

    @Transactional
    public BlockchainAddressWatchEntity register(ExternalTransferEntity transfer, String address, String label) {
        if (transfer == null || transfer.getId() == null) {
            throw new IllegalArgumentException("Transfer is required to register an address watch.");
        }
        return repository.findByTransferId(transfer.getId()).orElseGet(() -> {
            BlockchainAddressWatchEntity watch = new BlockchainAddressWatchEntity();
            watch.setTransferId(transfer.getId());
            watch.setUserId(transfer.getUserId());
            watch.setWalletId(transfer.getWalletId());
            watch.setAddress(address);
            watch.setLabel(label);
            watch.setStatus("WATCHING");
            return repository.save(watch);
        });
    }

    public Optional<BlockchainAddressWatchEntity> findActiveWatch(String address) {
        return repository.findTopByAddressAndStatusInOrderByCreatedAtDesc(address, ACTIVE_STATUSES);
    }

    public Optional<BlockchainAddressWatchEntity> findByTransferId(UUID transferId) {
        return repository.findByTransferId(transferId);
    }

    public List<BlockchainAddressWatchEntity> listActiveWatches(int limit) {
        List<BlockchainAddressWatchEntity> all = repository.findTop200ByStatusInOrderByCreatedAtAsc(ACTIVE_STATUSES);
        return all.size() <= limit ? all : all.subList(0, limit);
    }

    @Transactional
    public BlockchainAddressWatchEntity markDetected(
            BlockchainAddressWatchEntity watch,
            String txid,
            long amountSats,
            int confirmations) {
        watch.setObservedTxid(txid);
        watch.setObservedAmountSats(amountSats);
        watch.setConfirmations(confirmations);
        watch.setStatus(confirmations >= minimumConfirmations ? "CONFIRMED" : "DETECTED");
        if (watch.getDetectedAt() == null) {
            watch.setDetectedAt(LocalDateTime.now());
        }
        if (confirmations >= minimumConfirmations) {
            watch.setSettledAt(LocalDateTime.now());
        }
        return repository.save(watch);
    }

    @Transactional
    public BlockchainAddressWatchEntity markCompleted(BlockchainAddressWatchEntity watch, int confirmations) {
        watch.setConfirmations(confirmations);
        watch.setStatus("COMPLETED");
        if (watch.getSettledAt() == null) {
            watch.setSettledAt(LocalDateTime.now());
        }
        return repository.save(watch);
    }

    @Transactional
    public BlockchainAddressWatchEntity markCancelled(BlockchainAddressWatchEntity watch) {
        watch.setStatus("CANCELLED");
        return repository.save(watch);
    }
}
