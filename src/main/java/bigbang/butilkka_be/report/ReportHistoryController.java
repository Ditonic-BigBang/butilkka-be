package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.report.dto.ReportHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reportsHistory")
@RequiredArgsConstructor
public class ReportHistoryController {

    private final ReportHistoryService reportHistoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<ReportHistoryResponse>> getHistory(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        ReportHistoryResponse response = reportHistoryService.getHistory(Long.parseLong(userId), offset, limit);
        return ResponseEntity.ok(ApiResponse.ok("리포트 히스토리 조회 성공", response));
    }
}
