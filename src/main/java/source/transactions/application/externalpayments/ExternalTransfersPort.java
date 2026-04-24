package source.transactions.application.externalpayments;

import source.transactions.model.ExternalTransferEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalTransfersPort {

    ExternalTransferEntity save(ExternalTransferEntity transfer);

    List<ExternalTransferEntity> listByUserId(Long userId);

    Optional<ExternalTransferEntity> findById(UUID transferId);

    Optional<ExternalTransferEntity> findByIdAndUserId(UUID transferId, Long userId);

    Optional<ExternalTransferEntity> findByInvoiceId(String invoiceId);

    Optional<ExternalTransferEntity> findByBlockchainTxid(String blockchainTxid);

    List<ExternalTransferEntity> findInboundTransfersForMonitoring(int limit);

    List<ExternalTransferEntity> findOnchainTransfersForMonitoring(int limit);
}
