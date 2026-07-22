package source.auth.application.port.out;

import source.auth.model.entity.UserDataBase;

public interface AuthUserGateway {

    UserDataBase findByUsername(String username);

    boolean existsByUsername(String username);

    UserDataBase save(UserDataBase user);
}
