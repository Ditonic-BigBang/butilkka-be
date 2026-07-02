package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.user.dto.FavoriteAddRequest;
import bigbang.butilkka_be.user.dto.FavoriteItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping
    public ResponseEntity<ApiResponse<FavoriteItem>> add(
            @AuthenticationPrincipal String userId,
            @RequestBody FavoriteAddRequest request) {
        FavoriteItem item = favoriteService.add(Long.parseLong(userId), request.regionCode());
        return ResponseEntity.status(201).body(ApiResponse.created("관심 상권 추가 성공", item));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<FavoriteItem>>> list(
            @AuthenticationPrincipal String userId) {
        List<FavoriteItem> items = favoriteService.list(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.ok("관심 상권 조회 성공", items));
    }

    @DeleteMapping("/{regionCode}")
    public ResponseEntity<ApiResponse<Void>> remove(
            @AuthenticationPrincipal String userId,
            @PathVariable String regionCode) {
        favoriteService.remove(Long.parseLong(userId), regionCode);
        return ResponseEntity.ok(ApiResponse.ok("관심 상권 삭제 성공", null));
    }
}
