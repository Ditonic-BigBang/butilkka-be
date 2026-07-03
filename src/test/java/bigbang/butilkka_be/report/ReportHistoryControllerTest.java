package bigbang.butilkka_be.report;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.common.security.SecurityConfig;
import bigbang.butilkka_be.report.dto.ReportHistoryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportHistoryController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class ReportHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportHistoryService reportHistoryService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static Authentication authAs(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void getHistory_returnsOk() throws Exception {
        when(reportHistoryService.getHistory(eq(1L), eq(0), eq(20))).thenReturn(
                new ReportHistoryResponse(1, false, List.of(
                        new ReportHistoryResponse.ReportHistoryItem(1L, "2026Q4", "A", "요약"))));

        mockMvc.perform(get("/api/v1/reportsHistory")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reports[0].grade").value("A"));
    }

    @Test
    void getHistory_withOffsetAndLimit_passesParamsThrough() throws Exception {
        when(reportHistoryService.getHistory(eq(1L), eq(2), eq(5))).thenReturn(
                new ReportHistoryResponse(10, true, List.of()));

        mockMvc.perform(get("/api/v1/reportsHistory")
                        .param("offset", "2")
                        .param("limit", "5")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(10))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }
}
