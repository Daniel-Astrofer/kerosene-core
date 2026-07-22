package source.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import source.content.model.entity.HomeStageImpressionEntity;

import java.time.Instant;
import java.util.Optional;

public interface HomeStageImpressionRepository extends JpaRepository<HomeStageImpressionEntity, Long> {

    Optional<HomeStageImpressionEntity> findByUserIdAndContentFingerprint(Long userId, String contentFingerprint);

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
