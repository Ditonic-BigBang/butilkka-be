package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.user.dto.NotificationSettingsResponse;
import bigbang.butilkka_be.user.dto.NotificationSettingsUpdateRequest;
import bigbang.butilkka_be.user.dto.StoreResponse;
import bigbang.butilkka_be.user.dto.StoreUpdateRequest;
import bigbang.butilkka_be.user.dto.UserResponse;
import bigbang.butilkka_be.user.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
            @AuthenticationPrincipal String userId) {
        UserResponse response = userService.getMe(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.ok("사용자 정보 조회 성공", response));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @AuthenticationPrincipal String userId,
            @RequestBody UserUpdateRequest request) {
        UserResponse response = userService.updateProfile(Long.parseLong(userId), request);
        return ResponseEntity.ok(ApiResponse.ok("사용자 정보 수정 성공", response));
    }

    @PutMapping("/me/store")
    public ResponseEntity<ApiResponse<StoreResponse>> updateStore(
            @AuthenticationPrincipal String userId,
            @RequestBody StoreUpdateRequest request) {
        StoreResponse response = userService.updateStore(Long.parseLong(userId), request);
        return ResponseEntity.ok(ApiResponse.ok("가게 정보 저장 성공", response));
    }

    @GetMapping("/me/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> getNotificationSettings(
            @AuthenticationPrincipal String userId) {
        NotificationSettingsResponse response = userService.getNotificationSettings(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.ok("알림 설정 조회 성공", response));
    }

    @PatchMapping("/me/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> updateNotificationSettings(
            @AuthenticationPrincipal String userId,
            @RequestBody NotificationSettingsUpdateRequest request) {
        NotificationSettingsResponse response = userService.updateNotificationSettings(Long.parseLong(userId), request);
        return ResponseEntity.ok(ApiResponse.ok("알림 설정 변경 성공", response));
    }
}
