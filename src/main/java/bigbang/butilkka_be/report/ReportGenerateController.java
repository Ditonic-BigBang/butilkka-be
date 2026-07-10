package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.report.dto.ReportGenerateRequest;
import bigbang.butilkka_be.report.dto.ReportGenerateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportGenerateController {

    private final ReportGenerateService reportGenerateService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<ReportGenerateResponse>> generate(
            @RequestBody ReportGenerateRequest request) {
        ReportGenerateResponse response = reportGenerateService.generate(request);
        return ResponseEntity.ok(ApiResponse.ok("리포트 생성 성공", response));
    }
}
