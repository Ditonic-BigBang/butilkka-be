package bigbang.butilkka_be.lookup;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.lookup.dto.LookupResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LookupController.class)
@AutoConfigureMockMvc(addFilters = false)
class LookupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RegionLookupService regionLookupService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void lookup_withValidCoordinates_returnsOk() throws Exception {
        when(regionLookupService.lookup(anyDouble(), anyDouble()))
                .thenReturn(LookupResponse.of("1114055000", "명동", "11140", "중구"));

        mockMvc.perform(get("/api/v1/lookup").param("lat", "37.5665").param("lng", "126.9780"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regionName").value("명동"));
    }

    @Test
    void lookup_withMissingLat_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/lookup").param("lng", "126.9780"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lookup_withNonNumericLat_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/lookup").param("lat", "abc").param("lng", "126.9780"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void lookup_whenServiceThrowsNotFound_returnsNotFound() throws Exception {
        when(regionLookupService.lookup(anyDouble(), anyDouble()))
                .thenThrow(AppException.notFound("해당 좌표에 대한 행정동 정보를 찾을 수 없습니다"));

        mockMvc.perform(get("/api/v1/lookup").param("lat", "37.5665").param("lng", "126.9780"))
                .andExpect(status().isNotFound());
    }
}
