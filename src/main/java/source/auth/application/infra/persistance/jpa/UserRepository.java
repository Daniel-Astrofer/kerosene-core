package source.auth.application.infra.persistance.jpa;

import source.auth.model.entity.UserDataBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserDataBase, Long> {

    Optional<UserDataBase> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndPassphrase(String username, String passphrase);


}
