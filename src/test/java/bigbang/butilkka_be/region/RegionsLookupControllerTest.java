package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.region.dto.RegionLookupCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RegionsLookupController.class)
@AutoConfigureMockMvc(addFilters = false)
class RegionsLookupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionsLookupService regionsLookupService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void lookup_byKeyword_returnsOk() throws Exception {
        when(regionsLookupService.lookup(eq("역삼"), isNull(), isNull()))
                .thenReturn(List.of(new RegionLookupCandidate(
                        "1168064000", "역삼1동", "서울특별시 강남구 역삼1동", 37.5, 127.03)));

        mockMvc.perform(get("/api/v1/regions/lookup").param("keyword", "역삼"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].regionName").value("역삼1동"));
    }

    @Test
    void lookup_byCoordinate_returnsOk() throws Exception {
        when(regionsLookupService.lookup(isNull(), eq(37.5), eq(127.03)))
                .thenReturn(List.of(new RegionLookupCandidate(
                        "1168064000", "역삼1동", "서울특별시 강남구 역삼1동", 37.5, 127.03)));

        mockMvc.perform(get("/api/v1/regions/lookup").param("lat", "37.5").param("lng", "127.03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].regionName").value("역삼1동"));
    }
}
