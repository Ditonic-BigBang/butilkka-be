package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.common.security.SecurityConfig;
import bigbang.butilkka_be.user.dto.NotificationSettingsResponse;
import bigbang.butilkka_be.user.dto.StoreResponse;
import bigbang.butilkka_be.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static Authentication authAs(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void getMe_withValidRequest_returnsOk() throws Exception {
        when(userService.getMe(eq(1L)))
                .thenReturn(new UserResponse(1L, "김민수", false, false, null));

        mockMvc.perform(get("/api/v1/users/me")
                        .with(authentication(authAs("1")))
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("김민수"))
                .andExpect(jsonPath("$.data.store").doesNotExist());
    }

    @Test
    void getMe_withStore_returnsStoreInfo() throws Exception {
        when(userService.getMe(eq(1L)))
                .thenReturn(new UserResponse(1L, "김민수", true, false,
                        new UserResponse.StoreInfo("1168064000", "역삼1동", "CS100001", "한식음식점", 37.5, 127.03, "민수네 한식당", "서울시 강남구 역삼동", LocalDate.of(2022, 3, 15))));

        mockMvc.perform(get("/api/v1/users/me")
                        .with(authentication(authAs("1")))
                        .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.store.regionName").value("역삼1동"));
    }

    @Test
    void updateStore_withValidRequest_returnsOk() throws Exception {
        when(userService.updateStore(eq(1L), any()))
                .thenReturn(new StoreResponse(
                        "1168064000", "역삼1동", "CS100001", "한식음식점",
                        37.5, 127.03, "민수네 한식당", LocalDate.of(2022, 3, 15)));

        mockMvc.perform(put("/api/v1/users/me/store")
                        .with(authentication(authAs("1")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "regionCode": "1168064000",
                                  "categoryCode": "CS100001",
                                  "lat": 37.5,
                                  "lng": 127.03,
                                  "storeName": "민수네 한식당",
                                  "storeOpenDate": "2022-03-15"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regionName").value("역삼1동"));
    }

    @Test
    void updateProfile_withValidRequest_returnsOk() throws Exception {
        when(userService.updateProfile(eq(1L), any()))
                .thenReturn(new UserResponse(1L, "김철수", false, false, null));

        mockMvc.perform(patch("/api/v1/users/me")
                        .with(authentication(authAs("1")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "김철수"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("김철수"));
    }

    @Test
    void getNotificationSettings_returnsOk() throws Exception {
        when(userService.getNotificationSettings(eq(1L)))
                .thenReturn(new NotificationSettingsResponse(true, true, false));

        mockMvc.perform(get("/api/v1/users/me/notification-settings")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.smsAlert").value(true))
                .andExpect(jsonPath("$.data.urgentAlert").value(false));
    }

    @Test
    void updateNotificationSettings_withValidRequest_returnsOk() throws Exception {
        when(userService.updateNotificationSettings(eq(1L), any()))
                .thenReturn(new NotificationSettingsResponse(true, true, true));

        mockMvc.perform(patch("/api/v1/users/me/notification-settings")
                        .with(authentication(authAs("1")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "urgentAlert": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.urgentAlert").value(true));
    }
}
