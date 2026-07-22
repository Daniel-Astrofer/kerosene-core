package source.auth.application.infra.persistence.jpa;

import org.springframework.stereotype.Component;

import source.auth.application.port.out.AuthUserGateway;
import source.auth.model.entity.UserDataBase;

@Component
public class JpaAuthUserGateway implements AuthUserGateway {

    private final UserRepository repository;

    public JpaAuthUserGateway(UserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDataBase findByUsername(String username) {
        return repository.findByUsername(username);
    }

    @Override
    public boolean existsByUsername(String username) {
        return repository.existsByUsername(username);
    }

    @Override
    public UserDataBase save(UserDataBase user) {
        return repository.save(user);
    }
}
