package com.kerosene.auth.application.infra.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.kerosene.auth.model.entity.PasskeyCredential;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence queries and atomic signature-counter updates for WebAuthn passkey credentials. */
@Repository
public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, UUID> {
    /** Loads a credential by its binary identifier and fetches its user in the same query. */
    @EntityGraph(attributePaths = "user")
    Optional<PasskeyCredential> findByCredentialId(byte[] credentialId);

    /** Finds a credential only when its binary identifier and owning user both match. */
    Optional<PasskeyCredential> findByCredentialIdAndUserId(byte[] credentialId, Long userId);

    /** Lists all credential entities for a user. */
    List<PasskeyCredential> findByUserId(Long userId);

    /**
     * Projects a user's credential inventory, ordered by most recent access and then first enrollment.
     *
     * @param userId owning user identifier
     * @return inventory projection rows ordered newest first
     */
    @Query("""
            select new com.kerosene.auth.application.infra.persistence.jpa.PasskeyInventoryProjection(
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

    /**
     * Loads verification material and account state by credential ID.
     *
     * @param credentialId raw WebAuthn credential identifier bytes
     * @return minimal verification projection when the credential exists
     */
    @Query("""
            select new com.kerosene.auth.application.infra.persistence.jpa.PasskeyVerificationProjection(
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

    /**
     * Loads verification material only when credential ownership matches the supplied user.
     *
     * @param credentialId raw WebAuthn credential identifier bytes
     * @param userId required credential owner
     * @return verification projection when both credential and owner match
     */
    @Query("""
            select new com.kerosene.auth.application.infra.persistence.jpa.PasskeyVerificationProjection(
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

    /**
     * Advances an active credential counter only when the new value is strictly greater.
     * The conditional update is atomic and prevents replay of an older/equal authenticator assertion.
     *
     * @param credentialId credential identifier bytes
     * @param userId owner of the credential
     * @param newSignatureCount counter returned by verified authenticator data
     * @return affected row count; zero indicates stale counter, wrong owner, or inactive credential
     */
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

    /** Lists credentials by their user-handle bytes. */
    List<PasskeyCredential> findByUserHandle(byte[] userHandle);

    /** Finds the first credential tied to a user's installation identifier. */
    Optional<PasskeyCredential> findFirstByUserIdAndDeviceInstallId(Long userId, String deviceInstallId);

    /** Loads active credentials for an installation and fetches each owning user. */
    @EntityGraph(attributePaths = "user")
    @Query("""
            select p from PasskeyCredential p
             where p.deviceInstallId = :deviceInstallId
               and upper(coalesce(p.status, 'ACTIVE')) = 'ACTIVE'
            """)
    List<PasskeyCredential> findActiveByDeviceInstallId(@Param("deviceInstallId") String deviceInstallId);

    /** Deletes credentials associated with an installation ID across users. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from PasskeyCredential p where p.deviceInstallId = :deviceInstallId")
    int deleteByDeviceInstallId(@Param("deviceInstallId") String deviceInstallId);

    /** Deletes credentials only for the matching user and installation pair. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from PasskeyCredential p
             where p.user.id = :userId
               and p.deviceInstallId = :deviceInstallId
            """)
    int deleteByUserIdAndDeviceInstallId(
            @Param("userId") Long userId,
            @Param("deviceInstallId") String deviceInstallId);
}
