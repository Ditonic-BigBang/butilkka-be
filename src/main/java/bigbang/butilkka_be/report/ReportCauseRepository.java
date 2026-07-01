package bigbang.butilkka_be.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportCauseRepository extends JpaRepository<ReportCause, Long> {
    List<ReportCause> findByReportId(Long reportId);
}
