package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.region.dto.RegionMapResponse;
import bigbang.butilkka_be.region.dto.RegionRankingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionQueryController {

    private final RegionMapService regionMapService;
    private final RegionRankingService regionRankingService;

    @GetMapping("/map")
    public ResponseEntity<ApiResponse<RegionMapResponse>> getMap(
            @RequestParam(required = false) String quarter) {
        RegionMapResponse response = regionMapService.getMap(quarter);
        return ResponseEntity.ok(ApiResponse.ok("지도 데이터 조회 성공", response));
    }

    @GetMapping("/declineRanking")
    public ResponseEntity<ApiResponse<RegionRankingResponse>> getDeclineRanking(
            @RequestParam String order,
            @RequestParam(required = false) String quarter) {
        RegionRankingResponse response = regionRankingService.getRanking(order, quarter);
        return ResponseEntity.ok(ApiResponse.ok("순위 조회 성공", response));
    }
}
