package source.payments.service;

import org.springframework.stereotype.Component;
import source.payments.dto.PaymentQuoteResponse;
import source.payments.dto.PaymentStatusResponse;
import source.payments.model.PaymentIntentEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;

@Component
public class PaymentResponseMapper {

    private static final BigDecimal SATS_PER_BTC = new BigDecimal("100000000");

    public PaymentQuoteResponse toQuoteResponse(PaymentIntentEntity intent) {
        return new PaymentQuoteResponse(
                intent.getId(),
                intent.getQuoteExpiresAt(),
                intent.getRail(),
                intent.getFeeMode(),
                intent.getReceiverDisplayName(),
                satsToFiat(intent.getReceiverAmountSats(), intent.getFxRate()),
                intent.getReceiverAmountSats(),
                satsToFiat(intent.getTotalDebitSats(), intent.getFxRate()),
                intent.getTotalDebitSats(),
                satsToFiat(intent.getNetworkFeeSats(), intent.getFxRate()),
                intent.getNetworkFeeSats(),
                satsToFiat(intent.getKeroseneFeeSats(), intent.getFxRate()),
                intent.getKeroseneFeeSats(),
                warnings(intent.getWarnings()),
                true);
    }

    public PaymentStatusResponse toStatusResponse(PaymentIntentEntity intent) {
        return new PaymentStatusResponse(
                intent.getId(),
                intent.getStatus(),
                intent.getRail(),
                intent.getFeeMode(),
                intent.getReceiverDisplayName(),
                intent.getReceiverAmountSats(),
                intent.getTotalDebitSats(),
                intent.getNetworkFeeSats(),
                intent.getKeroseneFeeSats(),
                intent.getQuoteExpiresAt(),
                intent.getFailureCode(),
                intent.getFailureMessage(),
                warnings(intent.getWarnings()));
    }

    private String satsToFiat(long sats, BigDecimal fxRate) {
        return BigDecimal.valueOf(sats)
                .divide(SATS_PER_BTC, 8, RoundingMode.HALF_UP)
                .multiply(fxRate)
                .setScale(2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private List<String> warnings(String warnings) {
        if (warnings == null || warnings.isBlank()) {
            return List.of();
        }
        return Arrays.stream(warnings.split("\\|"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
