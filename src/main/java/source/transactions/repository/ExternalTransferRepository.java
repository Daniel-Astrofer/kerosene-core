package source.transactions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.transactions.model.ExternalTransferEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExternalTransferRepository extends JpaRepository<ExternalTransferEntity, UUID> {

    List<ExternalTransferEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ExternalTransferEntity> findByIdAndUserId(UUID id, Long userId);

    List<ExternalTransferEntity> findTop200ByStatusInAndTransferTypeInOrderByCreatedAtAsc(
            Collection<String> statuses,
            Collection<String> transferTypes);
}
