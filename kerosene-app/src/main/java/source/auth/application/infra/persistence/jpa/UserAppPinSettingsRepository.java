package source.auth.application.infra.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import source.auth.model.entity.UserAppPinSettings;

import java.util.Optional;

@Repository
public interface UserAppPinSettingsRepository extends JpaRepository<UserAppPinSettings, Long> {

    Optional<UserAppPinSettings> findByUserIdAndDeviceHash(Long userId, String deviceHash);
}
