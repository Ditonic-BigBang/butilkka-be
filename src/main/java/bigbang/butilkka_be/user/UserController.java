package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.user.dto.StoreResponse;
import bigbang.butilkka_be.user.dto.StoreUpdateRequest;
import bigbang.butilkka_be.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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

    @PutMapping("/me/store")
    public ResponseEntity<ApiResponse<StoreResponse>> updateStore(
            @AuthenticationPrincipal String userId,
            @RequestBody StoreUpdateRequest request) {
        StoreResponse response = userService.updateStore(Long.parseLong(userId), request);
        return ResponseEntity.ok(ApiResponse.ok("가게 정보 저장 성공", response));
    }
}
