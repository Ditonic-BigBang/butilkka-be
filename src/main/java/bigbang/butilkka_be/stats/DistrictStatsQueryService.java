package bigbang.butilkka_be.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DistrictStatsQueryService {

    private final DistrictStatsRepository districtStatsRepository;

    public List<DistrictStats> latestPerDistrict() {
        Map<String, DistrictStats> latestByDistrict = new LinkedHashMap<>();
        for (DistrictStats stats : districtStatsRepository.findAll()) {
            latestByDistrict.merge(stats.getDistrictCode(), stats, DistrictStatsQueryService::laterOf);
        }
        return List.copyOf(latestByDistrict.values());
    }

    public List<DistrictStats> forQuarter(int year, int quarter) {
        return districtStatsRepository.findByYearAndQuarter(year, quarter);
    }

    public String getLatestQuarterLabel() {
        return districtStatsRepository.findAll().stream()
                .max(Comparator.comparingInt(DistrictStats::getYear).thenComparingInt(DistrictStats::getQuarter))
                .map(s -> s.getYear() + "Q" + s.getQuarter())
                .orElse(null);
    }

    private static DistrictStats laterOf(DistrictStats a, DistrictStats b) {
        if (!a.getYear().equals(b.getYear())) {
            return a.getYear() > b.getYear() ? a : b;
        }
        return a.getQuarter() >= b.getQuarter() ? a : b;
    }
}
