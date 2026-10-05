package com.kerosene.auth.application.infra.persistence.jpa;

import com.kerosene.auth.model.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Persistence queries for a user's enrolled device records. */
@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    /** Finds one user-device row by owner ID. */
    Optional<UserDevice> findByUserId(Long id);

    /** Lists every device row owned by the user. */
    java.util.List<UserDevice> findAllByUserId(Long userId);

}
