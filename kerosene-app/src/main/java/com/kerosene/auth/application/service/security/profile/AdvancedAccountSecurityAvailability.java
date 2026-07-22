package source.auth.application.service.security.profile;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import source.auth.AuthExceptions;
import source.auth.model.enums.AccountSecurityType;

@Component
public class AdvancedAccountSecurityAvailability {

    private final boolean advancedModesEnabled;

    public AdvancedAccountSecurityAvailability(
            @Value("${account.security.advanced-modes-enabled:false}") boolean advancedModesEnabled) {
        this.advancedModesEnabled = advancedModesEnabled;
    }

    public boolean isEnabled() {
        return advancedModesEnabled;
    }

    public void assertSupported(AccountSecurityType accountSecurityType) {
        if (accountSecurityType == AccountSecurityType.SHAMIR
                || accountSecurityType == AccountSecurityType.MULTISIG_2FA) {
            if (!advancedModesEnabled) {
                throw new AuthExceptions.InvalidCredentials(
                        "Advanced account security modes are not available in this build yet.");
            }
        }
    }
}
