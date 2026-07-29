package com.kerosene.auth.application.port.out;

import com.kerosene.auth.model.entity.UserDataBase;

public interface AuthUserGateway {

    UserDataBase findByUsername(String username);

    boolean existsByUsername(String username);

    UserDataBase save(UserDataBase user);
}
