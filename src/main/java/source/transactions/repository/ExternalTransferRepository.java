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

    Optional<ExternalTransferEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<ExternalTransferEntity> findTopByBlockchainTxidOrderByCreatedAtDesc(String blockchainTxid);

    Optional<ExternalTransferEntity> findTopByPaymentHashOrderByCreatedAtDesc(String paymentHash);

    List<ExternalTransferEntity> findTop200ByStatusInAndTransferTypeInOrderByCreatedAtAsc(
            Collection<String> statuses,
            Collection<String> transferTypes);

    List<ExternalTransferEntity> findTop200ByNetworkAndBlockchainTxidIsNotNullAndStatusInOrderByCreatedAtAsc(
            String network,
            Collection<String> statuses);

    List<ExternalTransferEntity> findTop200ByStatusInOrderByUpdatedAtAsc(Collection<String> statuses);

    @Query("""
            select coalesce(sum(t.totalDebitedBtc), 0)
            from ExternalTransferEntity t
            where t.network = :network
              and t.transferType = 'OUTBOUND_PAYMENT'
              and t.status in :statuses
            """)
    BigDecimal sumReservedOutboundByNetworkAndStatuses(String network, Collection<String> statuses);

    @Query("""
            select coalesce(sum(t.amountBtc), 0) + coalesce(sum(t.networkFeeBtc), 0)
            from ExternalTransferEntity t
            where t.network = :network
              and t.transferType = 'OUTBOUND_PAYMENT'
              and t.status in :statuses
            """)
    BigDecimal sumProjectedOutboundRailOutflowByNetworkAndStatuses(String network, Collection<String> statuses);

    @Query("""
            select coalesce(sum(t.platformFeeBtc), 0)
            from ExternalTransferEntity t
            where t.transferType = 'OUTBOUND_PAYMENT'
              and t.status in :statuses
            """)
    BigDecimal sumUnsettledPlatformFeesByStatuses(Collection<String> statuses);

    @Query("""
            select t.network as network,
                   count(t) as eventCount,
                   coalesce(sum(abs(t.amountBtc)), 0) as volumeBtc,
                   coalesce(sum(t.networkFeeBtc), 0) as feeBtc,
                   coalesce(sum(case when t.transferType <> 'OUTBOUND_PAYMENT' then coalesce(t.amountBtc, 0) else 0 end), 0) as inflowBtc,
                   coalesce(sum(case when t.transferType = 'OUTBOUND_PAYMENT' then coalesce(t.amountBtc, 0) else 0 end), 0) as outflowBtc
            from ExternalTransferEntity t
            group by t.network
            """)
    List<NetworkAggregate> aggregateOperationalMetricsByNetwork();

    @Query("""
            select t.status as status,
                   count(t) as eventCount
            from ExternalTransferEntity t
            group by t.status
            """)
    List<StatusAggregate> aggregateOperationalMetricsByStatus();

    interface NetworkAggregate {
        String getNetwork();

        long getEventCount();

        BigDecimal getVolumeBtc();

        BigDecimal getFeeBtc();

        BigDecimal getInflowBtc();

        BigDecimal getOutflowBtc();
    }

    interface StatusAggregate {
        String getStatus();

        long getEventCount();
    }
}
