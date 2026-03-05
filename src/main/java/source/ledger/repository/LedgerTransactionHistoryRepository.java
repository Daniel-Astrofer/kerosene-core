package source.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import source.ledger.entity.LedgerTransactionHistory;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface LedgerTransactionHistoryRepository extends JpaRepository<LedgerTransactionHistory, UUID> {

    @Transactional
    @Modifying
    @Query("DELETE FROM LedgerTransactionHistory l WHERE l.createdAt < :timestamp")
    int deleteByCreatedAtBefore(LocalDateTime timestamp);

    @Query("SELECT l FROM LedgerTransactionHistory l WHERE l.senderUserId = :userId OR l.receiverUserId = :userId ORDER BY l.createdAt DESC")
    java.util.List<LedgerTransactionHistory> findUserHistory(Long userId, Pageable pageable);

    @Transactional
    @Modifying
    @Query("UPDATE LedgerTransactionHistory l SET l.status = :status WHERE l.id = :id")
    void updateStatus(UUID id, String status);

    @Transactional
    @Modifying
    @Query("UPDATE LedgerTransactionHistory l SET l.status = :status, l.blockchainTxid = :txid WHERE l.id = :id")
    void updateStatusAndTxid(UUID id, String status, String txid);

    java.util.Optional<LedgerTransactionHistory> findByBlockchainTxid(String txid);
}
