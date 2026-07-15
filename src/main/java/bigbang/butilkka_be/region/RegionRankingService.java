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

        // districtRank가 있으면 CSV 순위 기준, 없으면 등급 기준 정렬
        boolean hasRankData = statsList.stream().anyMatch(s -> s.getDistrictRank() != null);

        List<DistrictStats> sorted;
        if (hasRankData) {
            // CSV 구순위(districtRank) 기준 정렬
            Comparator<DistrictStats> byRank = Comparator.comparingInt(s ->
                    s.getDistrictRank() != null ? s.getDistrictRank() : Integer.MAX_VALUE);
            if ("bottom".equals(order)) {
                byRank = byRank.reversed();
            }
            sorted = statsList.stream()
                    .filter(s -> s.getDistrictRank() != null)
                    .sorted(byRank).limit(5).toList();
        } else {
            // fallback: 등급 기준 정렬 (E→A for top, A→E for bottom)
            Comparator<DistrictStats> byGrade = Comparator.comparingInt(s -> {
                String grade = s.getDeclineGrade();
                int idx = grade == null ? -1 : "ABCDE".indexOf(grade);
                return idx < 0 ? Integer.MAX_VALUE : idx;
            });
            if ("top".equals(order)) {
                byGrade = byGrade.reversed();  // top = 위험한 순 (E먼저)
            }
            sorted = statsList.stream()
                    .filter(s -> s.getDeclineGrade() != null)
                    .sorted(byGrade).limit(5).toList();
        }

        List<RegionRankingItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            DistrictStats s = sorted.get(i);
            int rank = s.getDistrictRank() != null ? s.getDistrictRank() : (i + 1);
            items.add(toRankingItem(s, rank));
        }

        return new RegionRankingResponse(order, quarterLabel, items);
    }

    private RegionRankingItem toRankingItem(DistrictStats stats, int rank) {
        // direction 값을 FE 기대 형식으로 변환 (성장→UP, 쇠퇴→DOWN, 유지/정체→FLAT)
        String rawDirection = stats.getDirection();
        String direction;
        if ("성장".equals(rawDirection)) {
            direction = "UP";
        } else if ("쇠퇴".equals(rawDirection)) {
            direction = "DOWN";
        } else {
            direction = "FLAT";
        }
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
