package bigbang.butilkka_be.stats;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DistrictStatsRepository extends JpaRepository<DistrictStats, Long> {
    List<DistrictStats> findByYearAndQuarter(Integer year, Integer quarter);
    List<DistrictStats> findByDistrictCodeOrderByYearAscQuarterAsc(String districtCode);
}
