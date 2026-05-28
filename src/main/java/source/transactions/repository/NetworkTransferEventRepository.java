package source.transactions.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.transactions.model.NetworkTransferEventEntity;

import java.util.UUID;

@Repository
public interface NetworkTransferEventRepository extends JpaRepository<NetworkTransferEventEntity, UUID> {
    java.util.List<NetworkTransferEventEntity> findTop100ByOrderByCreatedAtDesc();
}
