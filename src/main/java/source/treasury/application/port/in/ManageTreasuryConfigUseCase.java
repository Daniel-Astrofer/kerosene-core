package source.treasury.application.port.in;

import source.ledger.dto.TreasuryAuditConfigResponseDTO;

import java.math.BigDecimal;

public interface ManageTreasuryConfigUseCase {

    TreasuryAuditConfigResponseDTO getGlobalConfigResponse();

    TreasuryAuditConfigResponseDTO updateGlobalConfig(BigDecimal maxWithdrawLimit, String auditXpub);
}
