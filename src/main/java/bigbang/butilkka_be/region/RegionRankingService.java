package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionRankingItem;
import bigbang.butilkka_be.region.dto.RegionRankingResponse;
import bigbang.butilkka_be.stats.DistrictStats;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class RegionRankingService {

    private static final Pattern QUARTER_PATTERN = Pattern.compile("(\\d{4})Q([1-4])");

    private final DistrictStatsQueryService districtStatsQueryService;

    public RegionRankingResponse getRanking(String order, String quarterParam) {
        if (!"top".equals(order) && !"bottom".equals(order)) {
            throw AppException.badRequest("지원하지 않는 지표 또는 정렬 기준입니다.");
        }

        List<DistrictStats> statsList;
        String quarterLabel;
        if (quarterParam == null || quarterParam.isBlank()) {
            statsList = districtStatsQueryService.latestPerDistrict();
            quarterLabel = districtStatsQueryService.getLatestQuarterLabel();
        } else {
            int[] parsed = parseQuarterLabel(quarterParam)
                    .orElseThrow(() -> AppException.badRequest("지원하지 않는 지표 또는 정렬 기준입니다."));
            statsList = districtStatsQueryService.forQuarter(parsed[0], parsed[1]);
            quarterLabel = quarterParam;
        }

        // CSV 구순위(districtRank) 기준으로 정렬
        Comparator<DistrictStats> byRank = Comparator.comparingInt(s ->
                s.getDistrictRank() != null ? s.getDistrictRank() : Integer.MAX_VALUE);
        if ("bottom".equals(order)) {
            byRank = byRank.reversed();
        }

        List<DistrictStats> sorted = statsList.stream()
                .filter(s -> s.getDistrictRank() != null)  // 순위 없는 데이터 제외
                .sorted(byRank).limit(5).toList();

        List<RegionRankingItem> items = sorted.stream()
                .map(s -> toRankingItem(s, s.getDistrictRank()))
                .toList();

        return new RegionRankingResponse(order, quarterLabel, items);
    }

    private RegionRankingItem toRankingItem(DistrictStats stats, int rank) {
        // DistrictStats에 이미 districtName이 있으므로 별도 조회 불필요
        String direction = stats.getDirection() != null ? stats.getDirection() : "FLAT";
        return new RegionRankingItem(rank, stats.getDistrictCode(), stats.getDistrictName(), stats.getDeclineGrade(), direction);
    }

    private static Optional<int[]> parseQuarterLabel(String label) {
        Matcher m = QUARTER_PATTERN.matcher(label);
        if (!m.matches()) {
            return Optional.empty();
        }
        return Optional.of(new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))});
    }
}
