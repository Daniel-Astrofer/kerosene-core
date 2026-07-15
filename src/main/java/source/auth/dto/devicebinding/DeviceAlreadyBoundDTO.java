package source.auth.dto.devicebinding;

/**
 * Payload returned when a deviceInstallId is already bound to another account.
 * Client must re-submit registration with confirmUnlinkDevice=true after user consent.
 */
public record DeviceAlreadyBoundDTO(
        String action,
        String deviceInstallId,
        String previousUsernameMasked,
        String message,
        String guidance) {

    public static final String ACTION_CONFIRM_UNLINK = "CONFIRM_UNLINK_DEVICE";

    public static DeviceAlreadyBoundDTO of(String deviceInstallId, String previousUsernameMasked) {
        String masked = previousUsernameMasked == null || previousUsernameMasked.isBlank()
                ? "outra conta"
                : previousUsernameMasked;
        return new DeviceAlreadyBoundDTO(
                ACTION_CONFIRM_UNLINK,
                deviceInstallId,
                masked,
                "Este aparelho ja esta vinculado a outra conta (" + masked + ").",
                "Ao continuar, a passkey/device-key da conta anterior sera removida deste dispositivo "
                        + "e ela so podera ser acessada com senha. Confirme para criar a nova conta neste aparelho.");
    }
}
