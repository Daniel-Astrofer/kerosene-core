package source.treasury.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import source.treasury.entity.PlatformRevenue;

import java.util.Optional;

@Repository
public interface PlatformRevenueRepository extends JpaRepository<PlatformRevenue, Long> {

    // O lucro acumulado global é o ID = 1
    @Query("SELECT r FROM PlatformRevenue r WHERE r.id = 1")
    Optional<PlatformRevenue> getGlobalRevenue();
}
