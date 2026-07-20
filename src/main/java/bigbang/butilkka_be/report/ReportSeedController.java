package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 테스트용 과거 리포트 시딩 컨트롤러 (배포 후 삭제 필요)
 */
@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor
public class ReportSeedController {

    private final ReportGenerateService reportGenerateService;

    @PostMapping("/seed")
    public ResponseEntity<ApiResponse<String>> seedPastReport(
            @RequestParam Long userId,
            @RequestParam int year,
            @RequestParam int quarter) {
        Report report = reportGenerateService.generateForPastQuarter(userId, year, quarter);
        return ResponseEntity.ok(ApiResponse.ok("과거 리포트 생성 완료", "reportId: " + report.getReportId()));
    }
}
