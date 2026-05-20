package source.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import source.ledger.entity.LedgerTransactionHistory;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
/**
 * Repository for the short-lived operational transaction buffer.
 *
 * Rows in financial.ledger_transaction_history are not a permanent statement.
 * They are retained briefly so the mobile app can synchronize local encrypted
 * history and so settlement status can converge. Enterprise surfaces should use
 * aggregate metrics and integrity proofs instead of exposing these rows.
 */
public interface LedgerTransactionHistoryRepository extends JpaRepository<LedgerTransactionHistory, UUID> {

    @Transactional
    @Modifying
    @Query("DELETE FROM LedgerTransactionHistory l WHERE l.createdAt < :timestamp")
    int deleteByCreatedAtBefore(LocalDateTime timestamp);

    @Query("SELECT l FROM LedgerTransactionHistory l WHERE l.senderUserId = :userId OR l.receiverUserId = :userId ORDER BY l.createdAt DESC")
    java.util.List<LedgerTransactionHistory> findUserHistory(Long userId, Pageable pageable);

    @Query("""
            SELECT l.id AS id,
                   l.transactionType AS transactionType,
                   l.amount AS amount,
                   l.status AS status,
                   l.senderUserId AS senderUserId,
                   l.receiverUserId AS receiverUserId,
                   l.networkFee AS networkFee,
                   l.blockchainTxid AS blockchainTxid,
                   l.createdAt AS createdAt,
                   l.confirmations AS confirmations
            FROM LedgerTransactionHistory l
            WHERE l.senderUserId = :userId OR l.receiverUserId = :userId
            ORDER BY l.createdAt DESC
            """)
    java.util.List<LedgerSyncEventView> findUserHistoryView(
            @Param("userId") Long userId,
            Pageable pageable);

    @Query("""
            SELECT l
            FROM LedgerTransactionHistory l
            WHERE (l.senderUserId = :userId OR l.receiverUserId = :userId)
              AND l.createdAt >= :start
              AND l.createdAt < :end
            ORDER BY l.createdAt DESC
            """)
    java.util.List<LedgerTransactionHistory> findMovementHistoryForUser(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

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
