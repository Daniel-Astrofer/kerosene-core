package source.transactions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.transactions.model.ProcessedTransactionEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProcessedTransactionRepository extends JpaRepository<ProcessedTransactionEntity, UUID> {

    boolean existsByTxid(String txid);

    Optional<ProcessedTransactionEntity> findByTxid(String txid);
}
