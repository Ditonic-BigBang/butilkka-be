package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionMapItem;
import bigbang.butilkka_be.region.dto.RegionMapResponse;
import bigbang.butilkka_be.stats.DistrictStats;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RegionMapService {

    private static final Pattern QUARTER_PATTERN = Pattern.compile("(\\d{4})Q([1-4])");

    private final DistrictStatsQueryService districtStatsQueryService;

    public RegionMapResponse getMap(String quarterParam) {
        List<DistrictStats> statsList;
        String quarterLabel;

        if (quarterParam == null || quarterParam.isBlank()) {
            statsList = districtStatsQueryService.latestPerDistrict();
            quarterLabel = districtStatsQueryService.getLatestQuarterLabel();
        } else {
            int[] parsed = parseQuarterLabel(quarterParam)
                    .orElseThrow(() -> AppException.badRequest("지원하지 않는 지표입니다."));
            statsList = districtStatsQueryService.forQuarter(parsed[0], parsed[1]);
            quarterLabel = quarterParam;
        }

        List<RegionMapItem> items = statsList.stream()
                .map(this::toMapItem)
                .toList();

        return new RegionMapResponse(quarterLabel, items);
    }

    private RegionMapItem toMapItem(DistrictStats stats) {
        // 구 기반이므로 regionCode=districtCode, regionName=districtName, district도 동일
        return new RegionMapItem(
                stats.getDistrictCode(),
                stats.getDistrictName(),
                stats.getDistrictName(),  // district field (구 기반이라 동일)
                stats.getDeclineGrade(),
                stats.getDistrictRank()
        );
    }

    private static Optional<int[]> parseQuarterLabel(String label) {
        Matcher m = QUARTER_PATTERN.matcher(label);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
    }
}
