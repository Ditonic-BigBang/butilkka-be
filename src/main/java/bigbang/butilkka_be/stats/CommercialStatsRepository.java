package bigbang.butilkka_be.stats;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommercialStatsRepository extends JpaRepository<CommercialStats, Long> {
    List<CommercialStats> findByRegionCodeAndQuarter(String regionCode, Integer quarter);
}
