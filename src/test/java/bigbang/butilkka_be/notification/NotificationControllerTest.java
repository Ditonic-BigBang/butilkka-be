package bigbang.butilkka_be.notification;

import bigbang.butilkka_be.common.security.JwtTokenProvider;
import bigbang.butilkka_be.common.security.SecurityConfig;
import bigbang.butilkka_be.notification.dto.NotificationListResponse;
import bigbang.butilkka_be.notification.dto.NotificationReadResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static Authentication authAs(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void getNotifications_returnsOk() throws Exception {
        when(notificationService.getNotifications(eq(1L), eq(0), eq(20))).thenReturn(
                new NotificationListResponse(1, false, List.of(
                        new NotificationListResponse.NotificationItem(
                                301L, "EMERGENCY", "제목", "내용", false,
                                LocalDateTime.of(2026, 6, 20, 9, 0)))));

        mockMvc.perform(get("/api/v1/notifications")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notifications[0].category").value("EMERGENCY"));
    }

    @Test
    void getNotifications_withOffsetAndLimit_passesParamsThrough() throws Exception {
        when(notificationService.getNotifications(eq(1L), eq(2), eq(5))).thenReturn(
                new NotificationListResponse(10, true, List.of()));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("offset", "2")
                        .param("limit", "5")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(10))
                .andExpect(jsonPath("$.data.hasNext").value(true));
    }

    @Test
    void markAsRead_returnsOk() throws Exception {
        when(notificationService.markAsRead(eq(1L), eq(301L))).thenReturn(
                new NotificationReadResponse(301L, true));

        mockMvc.perform(patch("/api/v1/notifications/301")
                        .with(authentication(authAs("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationId").value(301))
                .andExpect(jsonPath("$.data.isRead").value(true));
    }
}
