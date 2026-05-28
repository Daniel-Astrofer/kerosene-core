package source.auth.application.infra.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import source.auth.model.entity.PasskeyCredential;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, UUID> {
    @EntityGraph(attributePaths = "user")
    Optional<PasskeyCredential> findByCredentialId(byte[] credentialId);

    Optional<PasskeyCredential> findByCredentialIdAndUserId(byte[] credentialId, Long userId);

    List<PasskeyCredential> findByUserId(Long userId);

    @Query("""
            select new source.auth.application.infra.persistence.jpa.PasskeyInventoryProjection(
                p.credentialId,
                p.deviceName,
                p.brand,
                p.model,
                p.serialNumber,
                p.deviceInstallId,
                p.platform,
                p.browser,
                p.firstAccessAt,
                p.lastAccessAt,
                p.status,
                p.relyingPartyId,
                p.originHost
            )
            from PasskeyCredential p
            where p.user.id = :userId
            order by p.lastAccessAt desc, p.firstAccessAt desc
            """)
    List<PasskeyInventoryProjection> findInventoryByUserId(@Param("userId") Long userId);

    @Query("""
            select new source.auth.application.infra.persistence.jpa.PasskeyVerificationProjection(
                p.credentialId,
                p.publicKeyCose,
                p.signatureCount,
                p.status,
                p.relyingPartyId,
                p.originHost,
                u.id,
                u.username,
                u.isActive
            )
            from PasskeyCredential p
            join p.user u
            where p.credentialId = :credentialId
            """)
    Optional<PasskeyVerificationProjection> findVerificationByCredentialId(
            @Param("credentialId") byte[] credentialId);

    @Query("""
            select new source.auth.application.infra.persistence.jpa.PasskeyVerificationProjection(
                p.credentialId,
                p.publicKeyCose,
                p.signatureCount,
                p.status,
                p.relyingPartyId,
                p.originHost,
                u.id,
                u.username,
                u.isActive
            )
            from PasskeyCredential p
            join p.user u
            where p.credentialId = :credentialId
              and u.id = :userId
            """)
    Optional<PasskeyVerificationProjection> findVerificationByCredentialIdAndUserId(
            @Param("credentialId") byte[] credentialId,
            @Param("userId") Long userId);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update PasskeyCredential p
               set p.signatureCount = :newSignatureCount
             where p.credentialId = :credentialId
               and p.user.id = :userId
               and p.signatureCount < :newSignatureCount
               and upper(coalesce(p.status, 'ACTIVE')) = 'ACTIVE'
            """)
    int advanceSignatureCount(
            @Param("credentialId") byte[] credentialId,
            @Param("userId") Long userId,
            @Param("newSignatureCount") long newSignatureCount);

    List<PasskeyCredential> findByUserHandle(byte[] userHandle);

    Optional<PasskeyCredential> findFirstByUserIdAndDeviceInstallId(Long userId, String deviceInstallId);
}
