package source.auth.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AccountActivationStatusDTO(
        boolean activated,
        boolean canReceiveInbound,
        boolean requiresActivationDeposit,
        BigDecimal requiredAmountBtc,
        String paymentLinkId,
        String depositAddress,
        String paymentStatus,
        String warningMessage,
        LocalDateTime activatedAt) {

    public static final String INBOUND_BLOCKED_MESSAGE =
            "Para receber fundos dentro da plataforma, deposite algum valor primeiro.";

    public static AccountActivationStatusDTO from(UserDataBaseView user) {
        return new AccountActivationStatusDTO(
                user.isActive(),
                user.isActive(),
                !user.isActive(),
                BigDecimal.ZERO,
                null,
                null,
                null,
                user.isActive()
                        ? null
                        : INBOUND_BLOCKED_MESSAGE,
                user.activatedAt());
    }

    public interface UserDataBaseView {
        boolean isActive();

        LocalDateTime activatedAt();
    }
}
