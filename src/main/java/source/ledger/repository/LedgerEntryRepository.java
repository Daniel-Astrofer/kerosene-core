package source.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import source.ledger.entity.LedgerEntry;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * Calcula o passivo total exato devido aos usuários (liability).
     * Ignora se for o usuário PLATFORM, que é de onde tiramos lucros.
     */
    @Query("SELECT COALESCE(SUM(l.amountNet), 0) FROM LedgerEntry l WHERE l.status = 'PENDING' AND l.userId != 'PLATFORM'")
    BigDecimal calculateLiabilityToUsers();

    /**
     * Calcula o total pendente de lucros da plataforma para ser sacado via
     * Siphoning.
     */
    @Query("SELECT COALESCE(SUM(l.feeAmount), 0) FROM LedgerEntry l WHERE l.status = 'PENDING' AND l.userId = 'PLATFORM'")
    BigDecimal calculatePlatformProfitPending();

    @Query("SELECT COALESCE(SUM(l.feeAmount), 0) FROM LedgerEntry l WHERE l.status = 'PENDING' AND l.userId = 'PLATFORM' AND l.createdAt <= :cutoff")
    BigDecimal calculatePlatformProfitPendingUpTo(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Após o Siphon (Dono sacar), o Backend marca os relatórios PENDING como
     * 'COLLECTED'.
     */
    @Modifying
    @Transactional
    @Query("UPDATE LedgerEntry l SET l.status = 'COLLECTED' WHERE l.status = 'PENDING' AND l.userId = 'PLATFORM'")
    void markFeesAsCollected();

    @Modifying
    @Transactional
    @Query("UPDATE LedgerEntry l SET l.status = 'COLLECTED' WHERE l.status = 'PENDING' AND l.userId = 'PLATFORM' AND l.createdAt <= :cutoff")
    int markFeesAsCollectedUpTo(@Param("cutoff") LocalDateTime cutoff);
}
