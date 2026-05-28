package source.payments.dto;

import source.payments.model.PaymentEnums;

import java.util.List;

public record ReceivingCapabilitiesResponse(
        boolean canReceiveInternal,
        boolean canReceiveLightning,
        boolean canReceiveOnchain,
        PaymentEnums.PaymentRail preferredRail,
        List<String> missingRequirements,
        String receiverDisplayName,
        List<PaymentEnums.PaymentRail> availableRails,
        Limits limits) {

    public record Limits(
            String asset,
            List<String> fiatCurrencies,
            long minInternalSats,
            long minLightningSats,
            long minOnchainSats) {
    }
}
