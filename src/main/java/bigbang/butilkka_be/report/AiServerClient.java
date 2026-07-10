package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.report.dto.ReportGenerateRequest;
import bigbang.butilkka_be.report.dto.ReportGenerateResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class AiServerClient {

    private final RestClient restClient;
    private final String aiServerUrl;

    public AiServerClient(
            @Value("${ai.server.url}") String aiServerUrl,
            @Value("${ai.server.timeout}") int timeout) {
        this.aiServerUrl = aiServerUrl;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public ReportGenerateResponse generateReport(ReportGenerateRequest request) {
        String url = aiServerUrl + "/api/report/generate";
        log.info("AI 서버 리포트 생성 요청: {}", url);

        try {
            ReportGenerateResponse response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ReportGenerateResponse.class);

            log.info("AI 서버 리포트 생성 완료");
            return response;
        } catch (Exception e) {
            log.error("AI 서버 호출 실패: {}", e.getMessage());
            throw AppException.badRequest("AI 서버 호출에 실패했습니다: " + e.getMessage());
        }
    }
}
