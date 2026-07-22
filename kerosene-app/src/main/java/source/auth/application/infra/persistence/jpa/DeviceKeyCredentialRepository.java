package source.auth.application.infra.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import source.auth.model.entity.DeviceKeyCredential;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeviceKeyCredentialRepository extends JpaRepository<DeviceKeyCredential, UUID> {

    Optional<DeviceKeyCredential> findByCredentialId(String credentialId);

    Optional<DeviceKeyCredential> findByCredentialIdAndUserId(String credentialId, Long userId);

    List<DeviceKeyCredential> findByUserId(Long userId);

    @Query("""
            select count(d) > 0 from DeviceKeyCredential d
             where d.user.id = :userId
               and upper(coalesce(d.status, 'ACTIVE')) = 'ACTIVE'
            """)
    boolean existsActiveByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("""
            update DeviceKeyCredential d
               set d.counter = :newCounter,
                   d.lastUsedAt = :lastUsedAt
             where d.credentialId = :credentialId
               and d.user.id = :userId
               and upper(d.status) = 'ACTIVE'
               and d.counter < :newCounter
            """)
    int advanceCounter(
            @Param("credentialId") String credentialId,
            @Param("userId") Long userId,
            @Param("newCounter") long newCounter,
            @Param("lastUsedAt") LocalDateTime lastUsedAt);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "user")
    @Query("""
            select d from DeviceKeyCredential d
             where d.deviceInstallId = :deviceInstallId
               and upper(coalesce(d.status, 'ACTIVE')) = 'ACTIVE'
            """)
    List<DeviceKeyCredential> findActiveByDeviceInstallId(@Param("deviceInstallId") String deviceInstallId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DeviceKeyCredential d where d.deviceInstallId = :deviceInstallId")
    int deleteByDeviceInstallId(@Param("deviceInstallId") String deviceInstallId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from DeviceKeyCredential d
             where d.user.id = :userId
               and d.deviceInstallId = :deviceInstallId
            """)
    int deleteByUserIdAndDeviceInstallId(
            @Param("userId") Long userId,
            @Param("deviceInstallId") String deviceInstallId);
}
