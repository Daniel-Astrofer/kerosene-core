package source.mining.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.mining.entity.MiningRigOfferEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface MiningRigOfferRepository extends JpaRepository<MiningRigOfferEntity, Long> {

    List<MiningRigOfferEntity> findByActiveTrueOrderByAlgorithmAscDisplayNameAsc();

    Optional<MiningRigOfferEntity> findByIdAndActiveTrue(Long id);
}
