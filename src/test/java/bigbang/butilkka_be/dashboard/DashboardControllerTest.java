package bigbang.butilkka_be.dashboard;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.common.security.SecurityConfig;
import bigbang.butilkka_be.dashboard.dto.DashboardResponse;
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

@WebMvcTest(DashboardController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static Authentication authAs(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private static DashboardResponse sampleResponse() {
        DashboardResponse.MetricTrend trend = new DashboardResponse.MetricTrend(
                "DOWN", 5.5, 6170L, List.of(new DashboardResponse.Point("2026Q3", 121940.0)));
        return new DashboardResponse(
                new DashboardResponse.StoreInfo("1168064000", "역삼1동", "한식음식점", "강남구"),
                new DashboardResponse.Grade("C", "B", 50),
                "유동인구 감소와 공실 증가가 겹치는 주의 구간입니다.",
                new DashboardResponse.Metrics(trend, trend, trend));
    }

    @Test
    void getDashboard_returnsOk() throws Exception {
        when(dashboardService.getDashboard(eq(1L))).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/dashboard")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.store.regionName").value("역삼1동"))
                .andExpect(jsonPath("$.data.grade.current").value("C"))
                .andExpect(jsonPath("$.data.metrics.footTraffic.direction").value("DOWN"));
    }
}
