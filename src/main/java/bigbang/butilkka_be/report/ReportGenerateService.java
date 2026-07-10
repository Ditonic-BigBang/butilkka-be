package bigbang.butilkka_be.report;

import bigbang.butilkka_be.report.dto.ReportGenerateRequest;
import bigbang.butilkka_be.report.dto.ReportGenerateResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerateService {

    private final AiServerClient aiServerClient;

    public ReportGenerateResponse generate(ReportGenerateRequest request) {
        log.info("리포트 생성 요청 - region: {}, year: {}, quarter: {}",
                request.regionCode(), request.year(), request.quarter());

        return aiServerClient.generateReport(request);
    }
}
