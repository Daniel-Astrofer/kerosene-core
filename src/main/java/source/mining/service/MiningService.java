package source.mining.service;

import org.springframework.stereotype.Service;
import source.mining.dto.MiningAllocationRequestDTO;
import source.mining.dto.MiningAllocationResponseDTO;
import source.mining.dto.MiningRigOfferDTO;

import java.util.List;
import java.util.UUID;

@Service
public class MiningService {

    private final RigCatalog rigCatalog;
    private final MiningAllocationUseCase allocationUseCase;

    public MiningService(RigCatalog rigCatalog, MiningAllocationUseCase allocationUseCase) {
        this.rigCatalog = rigCatalog;
        this.allocationUseCase = allocationUseCase;
    }

    public List<MiningRigOfferDTO> listRigOffers() {
        return rigCatalog.listActiveOffers();
    }

    public MiningAllocationResponseDTO createAllocation(Long userId, MiningAllocationRequestDTO request) {
        return allocationUseCase.createAllocation(userId, request);
    }

    public List<MiningAllocationResponseDTO> listAllocations(Long userId) {
        return allocationUseCase.listAllocations(userId);
    }

    public MiningAllocationResponseDTO getAllocation(Long userId, UUID allocationId) {
        return allocationUseCase.getAllocation(userId, allocationId);
    }

    public MiningAllocationResponseDTO cancelAllocation(Long userId, UUID allocationId) {
        return allocationUseCase.cancelAllocation(userId, allocationId);
    }
}
