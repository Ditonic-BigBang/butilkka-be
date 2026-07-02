package bigbang.butilkka_be.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CommercialStatsQueryService {

    private final CommercialStatsRepository commercialStatsRepository;

    public List<CommercialStats> latestPerRegion() {
        Map<String, CommercialStats> latestByRegion = new LinkedHashMap<>();
        for (CommercialStats stats : commercialStatsRepository.findAll()) {
            latestByRegion.merge(stats.getRegionCode(), stats, CommercialStatsQueryService::laterOf);
        }
        return List.copyOf(latestByRegion.values());
    }

    public Optional<CommercialStats> latestForRegion(String regionCode) {
        List<CommercialStats> history = commercialStatsRepository.findByRegionCodeOrderByYearAscQuarterAsc(regionCode);
        if (history.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(history.get(history.size() - 1));
    }

    public List<CommercialStats> historyForRegion(String regionCode) {
        return commercialStatsRepository.findByRegionCodeOrderByYearAscQuarterAsc(regionCode);
    }

    public List<CommercialStats> forQuarter(int year, int quarter) {
        return commercialStatsRepository.findByYearAndQuarter(year, quarter);
    }

    public static Optional<int[]> parseQuarterLabel(String label) {
        if (label == null || !label.matches("\\d{4}Q[1-4]")) {
            return Optional.empty();
        }
        int year = Integer.parseInt(label.substring(0, 4));
        int quarter = Integer.parseInt(label.substring(5));
        return Optional.of(new int[]{year, quarter});
    }

    private static CommercialStats laterOf(CommercialStats a, CommercialStats b) {
        if (!a.getYear().equals(b.getYear())) {
            return a.getYear() > b.getYear() ? a : b;
        }
        return a.getQuarter() >= b.getQuarter() ? a : b;
    }
}
