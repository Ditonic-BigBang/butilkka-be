package bigbang.butilkka_be.dashboard;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.dashboard.dto.DashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @AuthenticationPrincipal String userId) {
        DashboardResponse response = dashboardService.getDashboard(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.ok("대시보드 조회 성공", response));
    }
}
