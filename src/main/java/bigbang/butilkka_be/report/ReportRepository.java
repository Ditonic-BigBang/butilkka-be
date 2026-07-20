package bigbang.butilkka_be.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByUserId(Long userId);

    Optional<Report> findByUserIdAndRegionCodeAndYearAndQuarter(Long userId, String regionCode, Integer year, Integer quarter);
}
