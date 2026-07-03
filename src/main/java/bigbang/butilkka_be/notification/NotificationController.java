package bigbang.butilkka_be.notification;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.notification.dto.NotificationListResponse;
import bigbang.butilkka_be.notification.dto.NotificationReadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> getNotifications(
            @AuthenticationPrincipal String userId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        NotificationListResponse response = notificationService.getNotifications(Long.parseLong(userId), offset, limit);
        return ResponseEntity.ok(ApiResponse.ok("알림 목록 조회 성공", response));
    }

    @PatchMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> markAsRead(
            @AuthenticationPrincipal String userId,
            @PathVariable Long notificationId) {
        NotificationReadResponse response = notificationService.markAsRead(Long.parseLong(userId), notificationId);
        return ResponseEntity.ok(ApiResponse.ok("알림 읽음 처리 성공", response));
    }
}
