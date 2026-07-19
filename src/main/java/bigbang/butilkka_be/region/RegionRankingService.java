package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionRankingItem;
import bigbang.butilkka_be.region.dto.RegionRankingResponse;
import bigbang.butilkka_be.stats.DistrictStats;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

        // 등급 우선 정렬 → 같은 등급 내 compositeScore 정렬
        // top (위험한 순): E→D→C→B→A, 점수 높은 순
        // bottom (안전한 순): A→B→C→D→E, 점수 낮은 순
        Comparator<DistrictStats> comparator = Comparator
                .comparingInt((DistrictStats s) -> gradeToIndex(s.getDeclineGrade()))
                .thenComparing((DistrictStats s) -> s.getCompositeScore() != null ? s.getCompositeScore() : BigDecimal.ZERO);

        if ("top".equals(order)) {
            // 위험한 순: 등급 역순(E먼저) + 점수 높은 순
            comparator = Comparator
                    .comparingInt((DistrictStats s) -> gradeToIndex(s.getDeclineGrade()))
                    .reversed()
                    .thenComparing((DistrictStats s) -> s.getCompositeScore() != null ? s.getCompositeScore() : BigDecimal.ZERO, Comparator.reverseOrder());
        }

        List<DistrictStats> sorted = statsList.stream()
                .filter(s -> s.getDeclineGrade() != null)
                .sorted(comparator)
                .limit(5)
                .toList();

        List<RegionRankingItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            DistrictStats s = sorted.get(i);
            items.add(toRankingItem(s, i + 1));
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

    private static int gradeToIndex(String grade) {
        if (grade == null) return Integer.MAX_VALUE;
        return switch (grade) {
            case "A" -> 0;
            case "B" -> 1;
            case "C" -> 2;
            case "D" -> 3;
            case "E" -> 4;
            default -> Integer.MAX_VALUE;
        };
    }
}
