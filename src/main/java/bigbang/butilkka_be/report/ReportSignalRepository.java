package bigbang.butilkka_be.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportSignalRepository extends JpaRepository<ReportSignal, Long> {
    List<ReportSignal> findByReportId(Long reportId);
}
