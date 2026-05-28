package source.transactions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.transactions.model.BlockchainAddressWatchEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BlockchainAddressWatchRepository extends JpaRepository<BlockchainAddressWatchEntity, UUID> {

    Optional<BlockchainAddressWatchEntity> findByTransferId(UUID transferId);

    Optional<BlockchainAddressWatchEntity> findTopByAddressAndStatusInOrderByCreatedAtDesc(
            String address,
            Collection<String> statuses);

    List<BlockchainAddressWatchEntity> findTop200ByStatusInOrderByCreatedAtAsc(Collection<String> statuses);
}
