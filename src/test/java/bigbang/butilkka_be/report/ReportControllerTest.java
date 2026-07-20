package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.common.security.SecurityConfig;
import bigbang.butilkka_be.report.dto.ReportDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import bigbang.butilkka_be.report.dto.ReportCaseListResponse;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportDetailService reportDetailService;

    @MockitoBean
    private ReportCaseService reportCaseService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static Authentication authAs(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private static ReportDetailResponse sampleResponse() {
        return new ReportDetailResponse(
                1L, "1168064000", "강남구", "역삼1동", "한식음식점",
                "2026Q4", "A", "성장형", 90, "한 줄 브리핑", "AI 전망",
                null, null,
                List.of(), List.of(), List.of(),
                new ReportDetailResponse.Decision("버티기", "현 위치 유지 권장", "의사결정 설명"),
                List.of(), null, false);
    }

    @Test
    void getLatest_returnsOk() throws Exception {
        when(reportDetailService.getLatest(eq(1L))).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/reports/latest")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regionName").value("역삼1동"));
    }

    @Test
    void getDetail_returnsOk() throws Exception {
        when(reportDetailService.getDetail(eq(1L), eq(1L))).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/reports/1")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quarter").value("2026Q4"));
    }

    @Test
    void getCases_returnsOk() throws Exception {
        when(reportCaseService.getCases(eq(1L), eq(1L), eq(0), eq(20))).thenReturn(
                new bigbang.butilkka_be.report.dto.ReportCaseListResponse(1, List.of(
                        new bigbang.butilkka_be.report.dto.ReportCaseListResponse.ReportCaseItem(
                                "case-1", "1168051000", "신사동", "요약", "상세 설명",
                                "태그1", "태그2", "태그3", "태그4",
                                new bigbang.butilkka_be.report.dto.ReportCaseListResponse.Period(2019, 2022)))));

        mockMvc.perform(get("/api/v1/reports/1/cases")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cases[0].regionName").value("신사동"));
    }
}
