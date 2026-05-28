package source.treasury.application.usecase;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import source.ledger.dto.TreasuryAuditConfigResponseDTO;
import source.treasury.application.port.in.ManageTreasuryConfigUseCase;
import source.treasury.application.port.out.TreasuryConfigPort;
import source.treasury.application.port.out.TreasuryXpubValidationPort;
import source.treasury.domain.model.TreasuryConfigState;

import java.math.BigDecimal;

@Service
@Transactional
public class TreasuryConfigInteractor implements ManageTreasuryConfigUseCase {

    private final TreasuryConfigPort treasuryConfigPort;
    private final TreasuryXpubValidationPort treasuryXpubValidationPort;

    public TreasuryConfigInteractor(
            TreasuryConfigPort treasuryConfigPort,
            TreasuryXpubValidationPort treasuryXpubValidationPort) {
        this.treasuryConfigPort = treasuryConfigPort;
        this.treasuryXpubValidationPort = treasuryXpubValidationPort;
    }

    @Override
    public TreasuryAuditConfigResponseDTO getGlobalConfigResponse() {
        return toResponse(treasuryConfigPort.loadOrCreateGlobalConfig());
    }

    @Override
    public TreasuryAuditConfigResponseDTO updateGlobalConfig(BigDecimal maxWithdrawLimit, String auditXpub) {
        TreasuryConfigState current = treasuryConfigPort.loadOrCreateGlobalConfig();

        BigDecimal resolvedMaxWithdrawLimit = current.maxWithdrawLimit();
        if (maxWithdrawLimit != null) {
            if (maxWithdrawLimit.signum() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "maxWithdrawLimit must be greater than zero.");
            }
            resolvedMaxWithdrawLimit = maxWithdrawLimit;
        }

        String resolvedAuditXpub = current.auditXpub();
        if (auditXpub != null) {
            resolvedAuditXpub = normalize(auditXpub);
            if (resolvedAuditXpub != null) {
                try {
                    treasuryXpubValidationPort.validate(resolvedAuditXpub);
                } catch (RuntimeException ex) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Invalid audit xpub provided.",
                            ex);
                }
            }
        }

        TreasuryConfigState saved = treasuryConfigPort.saveGlobalConfig(
                new TreasuryConfigState(resolvedMaxWithdrawLimit, resolvedAuditXpub, current.updatedAt()));
        return toResponse(saved);
    }

    private TreasuryAuditConfigResponseDTO toResponse(TreasuryConfigState config) {
        String xpub = normalize(config.auditXpub());
        return new TreasuryAuditConfigResponseDTO(
                config.maxWithdrawLimit(),
                xpub != null,
                abbreviateXpub(xpub),
                config.updatedAt());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String abbreviateXpub(String xpub) {
        if (xpub == null || xpub.length() <= 16) {
            return xpub;
        }
        return xpub.substring(0, 8) + "..." + xpub.substring(xpub.length() - 8);
    }
}
