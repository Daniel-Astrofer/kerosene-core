package source.auth.application.infra.persistance.jpa;

import source.auth.model.entity.UserDataBase;
import source.auth.model.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByUserId(Long id);

    Optional<UserDevice> findByIdAndDeviceHash(Long id, String deviceHash);

}
