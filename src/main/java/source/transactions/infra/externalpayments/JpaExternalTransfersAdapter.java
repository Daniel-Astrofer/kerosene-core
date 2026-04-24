package source.transactions.infra.externalpayments;

import org.springframework.stereotype.Component;
import source.transactions.application.externalpayments.ExternalTransfersPort;
import source.transactions.model.ExternalTransferEntity;
import source.transactions.repository.ExternalTransferRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class JpaExternalTransfersAdapter implements ExternalTransfersPort {
    private static final List<String> MONITORED_INBOUND_STATUSES = List.of("PENDING", "DETECTED", "CONFIRMED");
    private static final List<String> MONITORED_INBOUND_TYPES = List.of(
            "ADDRESS_ISSUE",
            "ONRAMP_PURCHASE",
            "INBOUND_INVOICE");
    private static final List<String> MONITORED_ONCHAIN_STATUSES = List.of("PENDING", "DETECTED", "CONFIRMED");

    private final ExternalTransferRepository externalTransferRepository;

    public JpaExternalTransfersAdapter(ExternalTransferRepository externalTransferRepository) {
        this.externalTransferRepository = externalTransferRepository;
    }

    @Override
    public ExternalTransferEntity save(ExternalTransferEntity transfer) {
        return externalTransferRepository.save(transfer);
    }

    @Override
    public List<ExternalTransferEntity> listByUserId(Long userId) {
        return externalTransferRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public Optional<ExternalTransferEntity> findById(UUID transferId) {
        return externalTransferRepository.findById(transferId);
    }

    @Override
    public Optional<ExternalTransferEntity> findByIdAndUserId(UUID transferId, Long userId) {
        return externalTransferRepository.findByIdAndUserId(transferId, userId);
    }

    @Override
    public Optional<ExternalTransferEntity> findByInvoiceId(String invoiceId) {
        return externalTransferRepository.findByInvoiceId(invoiceId);
    }

    @Override
    public Optional<ExternalTransferEntity> findByBlockchainTxid(String blockchainTxid) {
        return externalTransferRepository.findTopByBlockchainTxidOrderByCreatedAtDesc(blockchainTxid);
    }

    @Override
    public List<ExternalTransferEntity> findInboundTransfersForMonitoring(int limit) {
        List<ExternalTransferEntity> transfers = externalTransferRepository
                .findTop200ByStatusInAndTransferTypeInOrderByCreatedAtAsc(
                        MONITORED_INBOUND_STATUSES,
                        MONITORED_INBOUND_TYPES);
        return transfers.size() <= limit ? transfers : transfers.subList(0, limit);
    }

    @Override
    public List<ExternalTransferEntity> findOnchainTransfersForMonitoring(int limit) {
        List<ExternalTransferEntity> transfers = externalTransferRepository
                .findTop200ByNetworkAndBlockchainTxidIsNotNullAndStatusInOrderByCreatedAtAsc(
                        "ONCHAIN",
                        MONITORED_ONCHAIN_STATUSES);
        return transfers.size() <= limit ? transfers : transfers.subList(0, limit);
    }
}
