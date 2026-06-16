package source.kfe.dto;

import java.util.List;

public record KfeReceivingCapabilitiesResponse(
        boolean canReceiveInternal,
        boolean canReceiveLightning,
        boolean canReceiveOnchain,
        String preferredRail,
        List<String> missingRequirements,
        String receiverDisplayName,
        List<String> availableRails,
        Limits limits) {

    public record Limits(
            String asset,
            List<String> fiatCurrencies,
            long minInternalSats,
            long minLightningSats,
            long minOnchainSats) {
    }
}
