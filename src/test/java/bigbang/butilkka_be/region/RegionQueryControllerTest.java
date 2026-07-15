package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.region.dto.RegionMapItem;
import bigbang.butilkka_be.region.dto.RegionMapResponse;
import bigbang.butilkka_be.region.dto.RegionRankingItem;
import bigbang.butilkka_be.region.dto.RegionRankingResponse;
import bigbang.butilkka_be.region.dto.RegionSearchItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegionQueryController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegionQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionMapService regionMapService;

    @MockitoBean
    private RegionRankingService regionRankingService;

    @MockitoBean
    private RegionSearchService regionSearchService;

    @MockitoBean
    private MetricMapService metricMapService;

    @MockitoBean
    private MetricRankingService metricRankingService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void getMap_returnsOk() throws Exception {
        when(regionMapService.getMap(isNull()))
                .thenReturn(new RegionMapResponse("2026Q4", List.of(
                        new RegionMapItem("1168064000", "역삼1동", "강남구", "A"))));

        mockMvc.perform(get("/api/v1/regions/map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions[0].regionName").value("역삼1동"));
    }

    @Test
    void getRanking_returnsOk() throws Exception {
        when(regionRankingService.getRanking("top", null))
                .thenReturn(new RegionRankingResponse("top", "2026Q4", List.of(
                        new RegionRankingItem(1, "1168064000", "역삼1동", "A", "UP"))));

        mockMvc.perform(get("/api/v1/regions/declineRanking").param("order", "top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions[0].regionName").value("역삼1동"));
    }

    @Test
    void search_returnsOk() throws Exception {
        when(regionSearchService.search("역삼"))
                .thenReturn(List.of(new RegionSearchItem("1168064000", "역삼1동", "강남구")));

        mockMvc.perform(get("/api/v1/regions/search").param("keyword", "역삼"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].regionName").value("역삼1동"));
    }
}
