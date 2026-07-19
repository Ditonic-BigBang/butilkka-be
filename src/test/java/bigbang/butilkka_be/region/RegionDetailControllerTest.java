package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.region.dto.RegionDetailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegionDetailController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegionDetailControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionDetailService regionDetailService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void getDetail_returnsOk() throws Exception {
        when(regionDetailService.getDetail("1168064000", null)).thenReturn(new RegionDetailResponse(
                "1168064000", "강남구", "역삼1동", "2026Q4",
                new RegionDetailResponse.DeclineGradeSummary("A", "C", List.of()),
                null, null, null, null, null));

        mockMvc.perform(get("/api/v1/districts/1168064000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regionName").value("역삼1동"));
    }

    @Test
    void getDetail_withQuarter_returnsOk() throws Exception {
        when(regionDetailService.getDetail("11680", "2025Q3")).thenReturn(new RegionDetailResponse(
                "11680", "강남구", "강남구", "2025Q3",
                new RegionDetailResponse.DeclineGradeSummary("B", "C", List.of()),
                null, null, null, null, null));

        mockMvc.perform(get("/api/v1/districts/11680").param("quarter", "2025Q3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quarter").value("2025Q3"));
    }
}
