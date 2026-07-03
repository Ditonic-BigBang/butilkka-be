package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.report.dto.ReportDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportDetailService reportDetailService;

    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> getLatest(
            @AuthenticationPrincipal String userId) {
        ReportDetailResponse response = reportDetailService.getLatest(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.ok("리포트 조회 성공", response));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<ApiResponse<ReportDetailResponse>> getDetail(
            @AuthenticationPrincipal String userId,
            @PathVariable Long reportId) {
        ReportDetailResponse response = reportDetailService.getDetail(Long.parseLong(userId), reportId);
        return ResponseEntity.ok(ApiResponse.ok("리포트 상세 조회 성공", response));
    }
}
