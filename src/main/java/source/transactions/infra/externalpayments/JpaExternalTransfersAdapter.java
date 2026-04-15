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
    private static final List<String> MONITORED_INBOUND_STATUSES = List.of("PENDING", "CANCELLED");
    private static final List<String> MONITORED_INBOUND_TYPES = List.of(
            "ADDRESS_ISSUE",
            "ONRAMP_PURCHASE",
            "INBOUND_INVOICE");

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
    public Optional<ExternalTransferEntity> findByIdAndUserId(UUID transferId, Long userId) {
        return externalTransferRepository.findByIdAndUserId(transferId, userId);
    }

    @Override
    public List<ExternalTransferEntity> findInboundTransfersForMonitoring(int limit) {
        List<ExternalTransferEntity> transfers = externalTransferRepository
                .findTop200ByStatusInAndTransferTypeInOrderByCreatedAtAsc(
                        MONITORED_INBOUND_STATUSES,
                        MONITORED_INBOUND_TYPES);
        return transfers.size() <= limit ? transfers : transfers.subList(0, limit);
    }
}
