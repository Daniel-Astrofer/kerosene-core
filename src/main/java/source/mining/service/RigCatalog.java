package source.mining.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import source.mining.dto.MiningRigOfferDTO;
import source.mining.entity.MiningRigOfferEntity;
import source.mining.exception.MiningExceptions;
import source.mining.repository.MiningRigOfferRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class RigCatalog {

    private final MiningRigOfferRepository rigOfferRepository;

    public RigCatalog(MiningRigOfferRepository rigOfferRepository) {
        this.rigOfferRepository = rigOfferRepository;
    }

    @Transactional(readOnly = true)
    public List<MiningRigOfferDTO> listActiveOffers() {
        return rigOfferRepository.findByActiveTrueOrderByAlgorithmAscDisplayNameAsc().stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public MiningRigOfferEntity getActiveRig(Long rigId) {
        return rigOfferRepository.findByIdAndActiveTrue(rigId)
                .orElseThrow(() -> new MiningExceptions.RigNotFound("The selected mining rig is not available."));
    }

    @Transactional(readOnly = true)
    public MiningRigOfferEntity getRig(Long rigId) {
        return rigOfferRepository.findById(rigId)
                .orElseThrow(() -> new MiningExceptions.RigNotFound("The underlying rig was not found."));
    }

    public void reserveHashrate(MiningRigOfferEntity rig, BigDecimal allocatedHashrate) {
        ensureAvailableHashrate(rig, allocatedHashrate);
        rig.setAvailableHashrate(normalize(rig.getAvailableHashrate().subtract(allocatedHashrate)));
        rigOfferRepository.save(rig);
    }

    public void ensureAvailableHashrate(MiningRigOfferEntity rig, BigDecimal allocatedHashrate) {
        if (allocatedHashrate.compareTo(rig.getAvailableHashrate()) > 0) {
            throw new MiningExceptions.InvalidMiningAllocation("Requested hashrate exceeds current available capacity.");
        }
    }

    public void releaseHashrate(MiningRigOfferEntity rig, BigDecimal allocatedHashrate) {
        rig.setAvailableHashrate(normalize(rig.getAvailableHashrate().add(allocatedHashrate)));
        rigOfferRepository.save(rig);
    }

    private MiningRigOfferDTO toDTO(MiningRigOfferEntity entity) {
        return new MiningRigOfferDTO(
                entity.getId(),
                entity.getRigCode(),
                entity.getDisplayName(),
                entity.getAlgorithm(),
                entity.getHashUnit(),
                entity.getAvailableHashrate(),
                entity.getPricePerUnitDayBtc(),
                entity.getProjectedBtcYieldPerUnitDay(),
                entity.getMinRentalHours(),
                entity.getMaxRentalHours(),
                entity.getProvider());
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(8, RoundingMode.HALF_UP);
    }
}
