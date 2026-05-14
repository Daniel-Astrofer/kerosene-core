package source.ledger.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MerkleAuditRepository extends JpaRepository<MerkleAuditEntity, UUID> {

    /** Returns the most recent audit checkpoint. */
    Optional<MerkleAuditEntity> findTopByOrderByCreatedAtDesc();

    /** Returns the last N audit checkpoints, newest first. */
    @Query("SELECT m FROM MerkleAuditEntity m ORDER BY m.createdAt DESC")
    List<MerkleAuditEntity> findLatestCheckpoints(org.springframework.data.domain.Pageable pageable);
}
