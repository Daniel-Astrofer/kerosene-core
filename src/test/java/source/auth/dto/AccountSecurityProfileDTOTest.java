package source.auth.dto;

import org.junit.jupiter.api.Test;
import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountSecurityProfileDTOTest {

    @Test
    void multisigThresholdTwoDoesNotRequirePasskeyJustBecauseOneExists() {
        UserDataBase user = new UserDataBase();
        user.setAccountSecurity(AccountSecurityType.MULTISIG_2FA);
        user.setMultisigThreshold(2);

        AccountSecurityProfileDTO profile = AccountSecurityProfileDTO.fromUser(user, true);

        assertTrue(profile.requiredFactors().contains("PASSPHRASE"));
        assertTrue(profile.requiredFactors().contains("TOTP"));
        assertFalse(profile.requiredFactors().contains("PASSKEY"));
    }

    @Test
    void multisigThresholdThreeRequiresPasskey() {
        UserDataBase user = new UserDataBase();
        user.setAccountSecurity(AccountSecurityType.MULTISIG_2FA);
        user.setMultisigThreshold(3);

        AccountSecurityProfileDTO profile = AccountSecurityProfileDTO.fromUser(user, true);

        assertTrue(profile.requiredFactors().contains("PASSKEY"));
    }
}
