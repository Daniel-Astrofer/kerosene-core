package com.kerosene.auth.application.service.security.profile;

import org.springframework.stereotype.Service;

import com.kerosene.auth.dto.UserDTO;

@Service
public class AccountSecurityProfileResolver {

    private final AccountSecurityProfileChain chain;

    public AccountSecurityProfileResolver(AccountSecurityProfileChain chain) {
        this.chain = chain;
    }

    public void normalize(UserDTO user) {
        chain.normalize(new AccountSecurityProfileContext(user));
    }
}
