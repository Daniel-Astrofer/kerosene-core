package source.kfe.dto;

import java.util.List;

public record KfeColdWalletPsbtResponse(
        String psbt,
        String psbtHash,
        long feeSats,
        long amountSats,
        String destinationAddress,
        List<KfeColdWalletPsbtRequest.Input> inputs) {
}
