package bigbang.butilkka_be.store;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.store.dto.CreateStoreRequest;
import bigbang.butilkka_be.store.dto.StoreListResponse;
import bigbang.butilkka_be.store.dto.UpdateStoreRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users/me/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StoreListResponse>>> getStores(
            @AuthenticationPrincipal String userId) {
        List<StoreListResponse> response = storeService.getStores(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.ok("내 가게 목록 조회 성공", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<StoreListResponse>> createStore(
            @AuthenticationPrincipal String userId,
            @RequestBody CreateStoreRequest request) {
        StoreListResponse response = storeService.createStore(Long.parseLong(userId), request);
        return ResponseEntity.ok(ApiResponse.ok("가게 등록 성공", response));
    }

    @PatchMapping("/{storeId}")
    public ResponseEntity<ApiResponse<StoreListResponse>> updateStore(
            @AuthenticationPrincipal String userId,
            @PathVariable Long storeId,
            @RequestBody UpdateStoreRequest request) {
        StoreListResponse response = storeService.updateStore(Long.parseLong(userId), storeId, request);
        return ResponseEntity.ok(ApiResponse.ok("가게 정보 수정 성공", response));
    }

    @DeleteMapping("/{storeId}")
    public ResponseEntity<ApiResponse<Void>> deleteStore(
            @AuthenticationPrincipal String userId,
            @PathVariable Long storeId) {
        storeService.deleteStore(Long.parseLong(userId), storeId);
        return ResponseEntity.ok(ApiResponse.ok("가게 삭제 성공", null));
    }
}
