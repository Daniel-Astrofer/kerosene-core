package source.mining.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.mining.entity.MiningAllocationEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MiningAllocationRepository extends JpaRepository<MiningAllocationEntity, UUID> {

    List<MiningAllocationEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<MiningAllocationEntity> findByIdAndUserId(UUID id, Long userId);
}
