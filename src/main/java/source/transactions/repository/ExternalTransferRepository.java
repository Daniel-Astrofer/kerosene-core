package source.transactions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import source.transactions.model.ExternalTransferEntity;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExternalTransferRepository extends JpaRepository<ExternalTransferEntity, UUID> {

    List<ExternalTransferEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ExternalTransferEntity> findByIdAndUserId(UUID id, Long userId);

    Optional<ExternalTransferEntity> findById(UUID id);

    Optional<ExternalTransferEntity> findByInvoiceId(String invoiceId);

    Optional<ExternalTransferEntity> findTopByBlockchainTxidOrderByCreatedAtDesc(String blockchainTxid);

    Optional<ExternalTransferEntity> findTopByPaymentHashOrderByCreatedAtDesc(String paymentHash);

    List<ExternalTransferEntity> findTop200ByStatusInAndTransferTypeInOrderByCreatedAtAsc(
            Collection<String> statuses,
            Collection<String> transferTypes);

    List<ExternalTransferEntity> findTop200ByNetworkAndBlockchainTxidIsNotNullAndStatusInOrderByCreatedAtAsc(
            String network,
            Collection<String> statuses);

    @Query("""
            select coalesce(sum(t.totalDebitedBtc), 0)
            from ExternalTransferEntity t
            where t.network = :network
              and t.transferType = 'OUTBOUND_PAYMENT'
              and t.status in :statuses
            """)
    BigDecimal sumReservedOutboundByNetworkAndStatuses(String network, Collection<String> statuses);
}
