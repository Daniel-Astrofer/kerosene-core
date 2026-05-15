package source.auth.application.infra.persistance.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.auth.model.entity.HardwareCredential;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HardwareCredentialRepository extends JpaRepository<HardwareCredential, UUID> {
    List<HardwareCredential> findByUserId(Long userId);
    Optional<HardwareCredential> findByPublicKey(String publicKey);
}
