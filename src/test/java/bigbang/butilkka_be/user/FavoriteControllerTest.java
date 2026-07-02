package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.common.security.SecurityConfig;
import bigbang.butilkka_be.user.dto.FavoriteItem;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FavoriteController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class FavoriteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FavoriteService favoriteService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static Authentication authAs(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void add_returnsCreated() throws Exception {
        when(favoriteService.add(eq(1L), eq("1168064000")))
                .thenReturn(new FavoriteItem("1168064000", "역삼1동", "강남구", null));

        mockMvc.perform(post("/api/v1/favorites")
                        .with(authentication(authAs("1")))
                        .contentType("application/json")
                        .content("{\"regionCode\": \"1168064000\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.regionName").value("역삼1동"));
    }

    @Test
    void list_returnsOk() throws Exception {
        when(favoriteService.list(1L)).thenReturn(List.of(new FavoriteItem("1168064000", "역삼1동", "강남구", "A")));

        mockMvc.perform(get("/api/v1/favorites").with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].regionName").value("역삼1동"));
    }

    @Test
    void remove_returnsOk() throws Exception {
        mockMvc.perform(delete("/api/v1/favorites/1168064000").with(authentication(authAs("1"))))
                .andExpect(status().isOk());
    }
}
