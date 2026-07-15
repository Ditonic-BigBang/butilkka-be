package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionRankingItem;
import bigbang.butilkka_be.region.dto.RegionRankingResponse;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionRankingService {

    private static final String GRADE_ORDER = "ABCDE";

    private final CommercialStatsQueryService commercialStatsQueryService;
    private final RegionRepository regionRepository;

    public RegionRankingResponse getRanking(String order, String quarterParam) {
        if (!"top".equals(order) && !"bottom".equals(order)) {
            throw AppException.badRequest("지원하지 않는 지표 또는 정렬 기준입니다.");
        }

        List<CommercialStats> statsList;
        String quarterLabel;
        if (quarterParam == null || quarterParam.isBlank()) {
            statsList = commercialStatsQueryService.latestPerRegion();
            quarterLabel = resolveLatestLabel(statsList);
        } else {
            int[] parsed = CommercialStatsQueryService.parseQuarterLabel(quarterParam)
                    .orElseThrow(() -> AppException.badRequest("지원하지 않는 지표 또는 정렬 기준입니다."));
            statsList = commercialStatsQueryService.forQuarter(parsed[0], parsed[1]);
            quarterLabel = quarterParam;
        }

        Comparator<CommercialStats> byGrade = Comparator.comparingInt(s -> {
            String grade = s.getDeclineGrade();
            int idx = grade == null ? -1 : GRADE_ORDER.indexOf(grade);
            return idx < 0 ? Integer.MAX_VALUE : idx;  // null이나 알 수 없는 등급은 최하위로
        });
        if ("bottom".equals(order)) {
            byGrade = byGrade.reversed();
        }

        List<CommercialStats> sorted = statsList.stream()
                .filter(s -> s.getDeclineGrade() != null)  // 등급 없는 데이터 제외
                .sorted(byGrade).limit(5).toList();

        List<RegionRankingItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            items.add(toRankingItem(sorted.get(i), i + 1));
        }

        return new RegionRankingResponse(order, quarterLabel, items);
    }

    private RegionRankingItem toRankingItem(CommercialStats stats, int rank) {
        Region region = regionRepository.findById(stats.getRegionCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        String direction = resolveDirection(stats);
        return new RegionRankingItem(rank, stats.getRegionCode(), region.getRegionName(), stats.getDeclineGrade(), direction);
    }

    private String resolveDirection(CommercialStats current) {
        List<CommercialStats> history = commercialStatsQueryService.historyForRegion(current.getRegionCode());
        int currentIndex = history.indexOf(current);
        if (currentIndex <= 0) {
            return "FLAT";
        }
        CommercialStats previous = history.get(currentIndex - 1);
        String currentGrade = current.getDeclineGrade();
        String previousGrade = previous.getDeclineGrade();
        if (currentGrade == null || previousGrade == null) {
            return "FLAT";
        }
        int currentRank = GRADE_ORDER.indexOf(currentGrade);
        int previousRank = GRADE_ORDER.indexOf(previousGrade);
        if (currentRank < previousRank) {
            return "UP";
        }
        if (currentRank > previousRank) {
            return "DOWN";
        }
        return "FLAT";
    }

    private String resolveLatestLabel(List<CommercialStats> statsList) {
        return statsList.stream()
                .max(Comparator.comparingInt(CommercialStats::getYear).thenComparingInt(CommercialStats::getQuarter))
                .map(s -> s.getYear() + "Q" + s.getQuarter())
                .orElse(null);
    }
}
