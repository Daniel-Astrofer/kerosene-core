package source.auth.application.infra.persistance.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.auth.model.entity.PasskeyCredential;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, UUID> {
    Optional<PasskeyCredential> findByCredentialId(byte[] credentialId);

    List<PasskeyCredential> findByUserId(Long userId);

    List<PasskeyCredential> findByUserHandle(byte[] userHandle);
}
