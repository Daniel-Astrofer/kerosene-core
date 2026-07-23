package source.auth.application.service.security.profile;

import source.auth.dto.UserDTO;
import source.auth.model.enums.AccountSecurityType;

public class AccountSecurityProfileContext {

    private final UserDTO user;
    private final AccountSecurityType securityType;

    public AccountSecurityProfileContext(UserDTO user) {
        this.user = user;
        this.securityType = user.getAccountSecurity() != null
                ? user.getAccountSecurity()
                : AccountSecurityType.STANDARD;
    }

    public UserDTO getUser() {
        return user;
    }

    public AccountSecurityType getSecurityType() {
        return securityType;
    }
}
