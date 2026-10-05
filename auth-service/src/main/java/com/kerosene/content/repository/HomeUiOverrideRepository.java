package com.kerosene.content.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.kerosene.content.model.entity.HomeUiOverrideEntity;

import java.time.Instant;
import java.util.List;

/** Persistence queries for time-valid overrides matching global, user, or segment scope. */
public interface HomeUiOverrideRepository extends JpaRepository<HomeUiOverrideEntity, Long> {

    /**
     * Selects enabled patches that are within their time window and target the user
     * or any supplied segment; rows are ordered for deterministic overlay precedence.
     * @param userId target account, nullable for anonymous requests
     * @param segments audience selectors derived from locale, balance view, and account bucket
     * @param now reference instant used for start and end date checks
     * @return matching override entities ordered by priority and identifier descending
     */
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
