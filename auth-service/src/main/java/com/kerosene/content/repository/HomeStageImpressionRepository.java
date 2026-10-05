package com.kerosene.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.kerosene.content.model.entity.HomeStageImpressionEntity;

import java.time.Instant;
import java.util.Optional;

/** Persistence queries for per-user stage acknowledgements and their active editions. */
public interface HomeStageImpressionRepository extends JpaRepository<HomeStageImpressionEntity, Long> {

    /** Finds a user's stored acknowledgement for an exact content edition. */
    /** @param userId account identifier @param contentFingerprint stable edition fingerprint @return matching row when one was recorded */
    Optional<HomeStageImpressionEntity> findByUserIdAndContentFingerprint(Long userId, String contentFingerprint);

    /** Checks for an acknowledgement that has not expired at the supplied instant. */
    /** @param userId account identifier @param fingerprint stable stage edition digest @param now reference time for expiry evaluation @return true when an active row exists */
    @Query("""
            SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END
            FROM HomeStageImpressionEntity i
            WHERE i.userId = :userId
              AND i.contentFingerprint = :fingerprint
              AND (i.expiresAt IS NULL OR i.expiresAt > :now)
            """)
    boolean existsActiveImpression(
            @Param("userId") Long userId,
            @Param("fingerprint") String fingerprint,
            @Param("now") Instant now);
}
