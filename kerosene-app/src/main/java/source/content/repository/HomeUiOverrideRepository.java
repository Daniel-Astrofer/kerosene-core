package source.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import source.content.model.entity.HomeUiOverrideEntity;

import java.time.Instant;
import java.util.List;

public interface HomeUiOverrideRepository extends JpaRepository<HomeUiOverrideEntity, Long> {

    @Query("""
            SELECT o FROM HomeUiOverrideEntity o
            WHERE o.active = true
              AND (o.startsAt IS NULL OR o.startsAt <= :now)
              AND (o.endsAt IS NULL OR o.endsAt > :now)
              AND (
                    o.scope = 'GLOBAL'
                 OR (o.scope = 'USER' AND o.userId = :userId)
                 OR (o.scope = 'SEGMENT' AND o.segmentKey IN :segments)
              )
            ORDER BY o.priority DESC, o.id DESC
            """)
    List<HomeUiOverrideEntity> findActiveMatching(
            @Param("userId") Long userId,
            @Param("segments") List<String> segments,
            @Param("now") Instant now);
}
