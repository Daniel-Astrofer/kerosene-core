package source.auth.application.infra.persistance.jpa;

import source.auth.model.entity.UserDataBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserDataBase, Long> {

    UserDataBase findByUsername(String username);

    boolean existsByUsername(String username);

    // ⚠️ existsByUsernameAndPassphrase was intentionally removed.
    // Passing a raw passphrase as a String into JPA leaks it into the JVM String
    // Pool.
    // Password verification MUST occur at the service layer:
    // 1. Retrieve entity with findByUsername(username)
    // 2. Validate with Argon2 / BCrypt against the stored hash using char[]
    // 3. Zero the char[] immediately after validation

}
