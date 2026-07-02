package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.region.dto.RegionLookupCandidate;
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
public class RegionsLookupController {

    private final RegionsLookupService regionsLookupService;

    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<List<RegionLookupCandidate>>> lookup(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        List<RegionLookupCandidate> candidates = regionsLookupService.lookup(keyword, lat, lng);
        return ResponseEntity.ok(ApiResponse.ok("상권 조회 성공", candidates));
    }
}
