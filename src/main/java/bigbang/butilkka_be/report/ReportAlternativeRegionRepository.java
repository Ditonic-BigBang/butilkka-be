package bigbang.butilkka_be.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportAlternativeRegionRepository extends JpaRepository<ReportAlternativeRegion, Long> {
    List<ReportAlternativeRegion> findByReportId(Long reportId);
}
