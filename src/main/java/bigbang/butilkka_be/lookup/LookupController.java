package bigbang.butilkka_be.lookup;

import bigbang.butilkka_be.common.response.ApiResponse;
import bigbang.butilkka_be.lookup.dto.LookupResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lookup")
@RequiredArgsConstructor
@Tag(name = "Lookup", description = "좌표 기반 행정동 조회 API")
public class LookupController {

    private final RegionLookupService regionLookupService;

    @GetMapping
    @Operation(summary = "좌표로 행정동 조회",
            description = "위도/경도 좌표를 받아 해당 위치의 행정동(상권) 정보를 반환합니다")
    public ResponseEntity<ApiResponse<LookupResponse>> lookup(
            @Parameter(description = "위도 (예: 37.5665)", required = true)
            @RequestParam double lat,

            @Parameter(description = "경도 (예: 126.9780)", required = true)
            @RequestParam double lng) {

        LookupResponse response = regionLookupService.lookup(lat, lng);
        return ResponseEntity.ok(ApiResponse.ok("행정동 조회 성공", response));
    }
}
