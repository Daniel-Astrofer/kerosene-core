package source.auth.application.service.security.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import source.auth.AuthExceptions;
import source.auth.dto.UserDTO;
import source.auth.model.enums.AccountSecurityType;

class AccountSecurityProfileResolverTest {

    private AccountSecurityProfileResolver resolver;

    @BeforeEach
    void setUp() {
        AccountSecurityProfileChain chain = new AccountSecurityProfileChain(List.of(
                new DefaultAccountSecurityProfileHandler(),
                new MultisigAccountSecurityProfileHandler(),
                new ShamirAccountSecurityProfileHandler()));
        resolver = new AccountSecurityProfileResolver(chain);
    }

    @Test
    void normalizeShouldApplyShamirRulesAndClearMultisigThreshold() {
        UserDTO dto = new UserDTO();
        dto.setAccountSecurity(AccountSecurityType.SHAMIR);
        dto.setShamirTotalShares(5);
        dto.setShamirThreshold(3);
        dto.setMultisigThreshold(2);

        resolver.normalize(dto);

        assertEquals(5, dto.getShamirTotalShares());
        assertEquals(3, dto.getShamirThreshold());
        assertNull(dto.getMultisigThreshold());
    }

    @Test
    void normalizeShouldApplyDefaultProfileForPasskeyMode() {
        UserDTO dto = new UserDTO();
        dto.setAccountSecurity(AccountSecurityType.PASSKEY);
        dto.setShamirTotalShares(7);
        dto.setShamirThreshold(4);
        dto.setMultisigThreshold(null);

        resolver.normalize(dto);

        assertNull(dto.getShamirTotalShares());
        assertNull(dto.getShamirThreshold());
        assertEquals(2, dto.getMultisigThreshold());
    }

    @Test
    void normalizeShouldRejectInvalidMultisigThreshold() {
        UserDTO dto = new UserDTO();
        dto.setAccountSecurity(AccountSecurityType.MULTISIG_2FA);
        dto.setMultisigThreshold(4);

        assertThrows(AuthExceptions.InvalidCredentials.class, () -> resolver.normalize(dto));
    }
}
