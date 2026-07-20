package bigbang.butilkka_be.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportSimilarCaseRepository extends JpaRepository<ReportSimilarCase, String> {
    List<ReportSimilarCase> findByReportId(Long reportId);
    void deleteByReportId(Long reportId);
}
