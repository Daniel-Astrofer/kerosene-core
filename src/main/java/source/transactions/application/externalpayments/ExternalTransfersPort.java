package source.transactions.application.externalpayments;

import source.transactions.model.ExternalTransferEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalTransfersPort {

    ExternalTransferEntity save(ExternalTransferEntity transfer);

    List<ExternalTransferEntity> listByUserId(Long userId);

    Optional<ExternalTransferEntity> findByIdAndUserId(UUID transferId, Long userId);

    List<ExternalTransferEntity> findInboundTransfersForMonitoring(int limit);
}
