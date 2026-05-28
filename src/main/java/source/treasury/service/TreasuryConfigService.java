package source.treasury.service;

import org.springframework.stereotype.Service;
import source.ledger.dto.TreasuryAuditConfigResponseDTO;
import source.treasury.application.port.in.ManageTreasuryConfigUseCase;

import java.math.BigDecimal;

@Service
public class TreasuryConfigService {

    private final ManageTreasuryConfigUseCase manageTreasuryConfigUseCase;

    public TreasuryConfigService(ManageTreasuryConfigUseCase manageTreasuryConfigUseCase) {
        this.manageTreasuryConfigUseCase = manageTreasuryConfigUseCase;
    }

    public TreasuryAuditConfigResponseDTO getGlobalConfigResponse() {
        return manageTreasuryConfigUseCase.getGlobalConfigResponse();
    }

    public TreasuryAuditConfigResponseDTO updateGlobalConfig(
            BigDecimal maxWithdrawLimit,
            String auditXpub) {
        return manageTreasuryConfigUseCase.updateGlobalConfig(maxWithdrawLimit, auditXpub);
    }
}
