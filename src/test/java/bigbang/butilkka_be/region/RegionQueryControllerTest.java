package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.region.dto.RegionMapItem;
import bigbang.butilkka_be.region.dto.RegionMapResponse;
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
}
