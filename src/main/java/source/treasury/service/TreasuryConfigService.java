package source.treasury.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import source.common.service.AddressDerivationService;
import source.ledger.dto.TreasuryAuditConfigResponseDTO;
import source.treasury.entity.TreasuryConfig;
import source.treasury.repository.TreasuryConfigRepository;

import java.math.BigDecimal;

@Service
public class TreasuryConfigService {

    private final TreasuryConfigRepository treasuryConfigRepository;
    private final AddressDerivationService addressDerivationService;

    public TreasuryConfigService(
            TreasuryConfigRepository treasuryConfigRepository,
            AddressDerivationService addressDerivationService) {
        this.treasuryConfigRepository = treasuryConfigRepository;
        this.addressDerivationService = addressDerivationService;
    }

    public TreasuryAuditConfigResponseDTO getGlobalConfigResponse() {
        return toResponse(getOrCreateGlobalConfig());
    }

    public TreasuryAuditConfigResponseDTO updateGlobalConfig(
            BigDecimal maxWithdrawLimit,
            String auditXpub) {
        TreasuryConfig config = getOrCreateGlobalConfig();

        if (maxWithdrawLimit != null) {
            if (maxWithdrawLimit.signum() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "maxWithdrawLimit must be greater than zero.");
            }
            config.setMaxWithdrawLimit(maxWithdrawLimit);
        }

        if (auditXpub != null) {
            String normalizedXpub = normalize(auditXpub);
            if (normalizedXpub != null) {
                try {
                    addressDerivationService.deriveAddressFromXpub(normalizedXpub, 0);
                } catch (RuntimeException ex) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Invalid audit xpub provided.",
                            ex);
                }
            }
            config.setAuditXpub(normalizedXpub);
        }

        TreasuryConfig saved = treasuryConfigRepository.save(config);
        return toResponse(saved);
    }

    private TreasuryConfig getOrCreateGlobalConfig() {
        return treasuryConfigRepository.getGlobalConfig()
                .orElseGet(() -> {
                    TreasuryConfig config = new TreasuryConfig();
                    config.setId(1L);
                    return treasuryConfigRepository.save(config);
                });
    }

    private TreasuryAuditConfigResponseDTO toResponse(TreasuryConfig config) {
        String xpub = normalize(config.getAuditXpub());
        return new TreasuryAuditConfigResponseDTO(
                config.getMaxWithdrawLimit(),
                xpub != null,
                abbreviateXpub(xpub),
                config.getUpdatedAt());
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
