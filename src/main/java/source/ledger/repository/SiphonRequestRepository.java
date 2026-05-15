package source.ledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.ledger.entity.SiphonRequest;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SiphonRequestRepository extends JpaRepository<SiphonRequest, UUID> {
    Optional<SiphonRequest> findByStatus(String status);
}
