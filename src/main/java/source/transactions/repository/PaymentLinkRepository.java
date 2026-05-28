package source.transactions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import source.transactions.model.PaymentLinkEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaymentLinkRepository extends JpaRepository<PaymentLinkEntity, String> {

    List<PaymentLinkEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PaymentLinkEntity> findTop200ByStatusAndExpiresAtAfterOrderByCreatedAtAsc(
            String status,
            LocalDateTime expiresAfter);

    @Query("""
            select p.status as status,
                   count(p) as linkCount,
                   coalesce(sum(p.amountBtc), 0) as amountBtc
            from PaymentLinkEntity p
            group by p.status
            """)
    List<StatusAggregate> aggregateOperationalMetricsByStatus();

    interface StatusAggregate {
        String getStatus();

        long getLinkCount();

        BigDecimal getAmountBtc();
    }
}
