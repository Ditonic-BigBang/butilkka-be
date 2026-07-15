package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.region.dto.MetricMapResponse;
import bigbang.butilkka_be.region.dto.MetricRankingResponse;
import bigbang.butilkka_be.region.dto.RegionMapResponse;
import bigbang.butilkka_be.region.dto.RegionRankingResponse;
import bigbang.butilkka_be.region.dto.RegionSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionQueryController {

    private final RegionMapService regionMapService;
    private final RegionRankingService regionRankingService;
    private final RegionSearchService regionSearchService;
    private final MetricMapService metricMapService;
    private final MetricRankingService metricRankingService;

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

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<RegionSearchItem>>> search(
            @RequestParam String keyword) {
        List<RegionSearchItem> result = regionSearchService.search(keyword);
        return ResponseEntity.ok(ApiResponse.ok("상권 검색 성공", result));
    }

    @GetMapping("/metricMap")
    public ResponseEntity<ApiResponse<MetricMapResponse>> getMetricMap(
            @RequestParam String metric,
            @RequestParam(required = false) String quarter) {
        MetricMapResponse response = metricMapService.getMetricMap(metric, quarter);
        return ResponseEntity.ok(ApiResponse.ok("지표별 지도 데이터 조회 성공", response));
    }

    @GetMapping("/metricRanking")
    public ResponseEntity<ApiResponse<MetricRankingResponse>> getMetricRanking(
            @RequestParam String metric,
            @RequestParam String order,
            @RequestParam(required = false) String quarter) {
        MetricRankingResponse response = metricRankingService.getMetricRanking(metric, order, quarter);
        return ResponseEntity.ok(ApiResponse.ok("지표별 순위 조회 성공", response));
    }
}
