package source.treasury.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import source.treasury.entity.TreasuryConfig;

import java.util.Optional;

@Repository
public interface TreasuryConfigRepository extends JpaRepository<TreasuryConfig, Long> {

    // Config global é a ID = 1
    @Query("SELECT t FROM TreasuryConfig t WHERE t.id = 1")
    Optional<TreasuryConfig> getGlobalConfig();
}
