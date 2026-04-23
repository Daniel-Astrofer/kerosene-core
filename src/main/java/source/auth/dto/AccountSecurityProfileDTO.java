package source.auth.dto;

import source.auth.model.entity.UserDataBase;
import source.auth.model.enums.AccountSecurityType;

import java.util.ArrayList;
import java.util.List;

public record AccountSecurityProfileDTO(
        AccountSecurityType accountSecurity,
        Integer shamirTotalShares,
        Integer shamirThreshold,
        Integer multisigThreshold,
        boolean passkeyAvailable,
        boolean passkeyEnabledForTransactions,
        List<String> requiredFactors) {

    public static AccountSecurityProfileDTO fromUser(UserDataBase user, boolean passkeyAvailable) {
        return new AccountSecurityProfileDTO(
                user.getAccountSecurity(),
                user.getShamirTotalShares(),
                user.getShamirThreshold(),
                user.getMultisigThreshold() != null ? user.getMultisigThreshold() : 2,
                passkeyAvailable,
                Boolean.TRUE.equals(user.getPasskeyEnabledForTransactions()),
                requiredFactorsFor(user, passkeyAvailable));
    }

    private static List<String> requiredFactorsFor(UserDataBase user, boolean passkeyAvailable) {
        List<String> factors = new ArrayList<>();

        if (user.getAccountSecurity() == null) {
            return factors;
        }

        switch (user.getAccountSecurity()) {
            case SHAMIR -> {
                factors.add("SLIP39_SHARES");
                factors.add("TOTP");
            }
            case MULTISIG_2FA -> {
                factors.add("PASSPHRASE");
                factors.add("TOTP");
                if (user.getMultisigThreshold() != null && user.getMultisigThreshold() >= 3) {
                    factors.add("PASSKEY");
                }
            }
            case PASSKEY -> factors.add("PASSKEY");
            case STANDARD -> {
                factors.add("PASSKEY");
            }
        }

        return factors;
    }
}
