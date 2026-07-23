package com.kerosene.auth.application.service.security.profile;

import com.kerosene.auth.dto.UserDTO;
import com.kerosene.auth.model.enums.AccountSecurityType;

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
