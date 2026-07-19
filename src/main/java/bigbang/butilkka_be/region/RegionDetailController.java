package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.region.dto.RegionDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/districts")
@RequiredArgsConstructor
public class RegionDetailController {

    private final RegionDetailService regionDetailService;

    @GetMapping("/{districtsCode}")
    public ResponseEntity<ApiResponse<RegionDetailResponse>> getDetail(
            @PathVariable("districtsCode") String districtsCode,
            @RequestParam(value = "quarter", required = false) String quarter) {
        RegionDetailResponse response = regionDetailService.getDetail(districtsCode, quarter);
        return ResponseEntity.ok(ApiResponse.ok("상권 상세 조회 성공", response));
    }
}
