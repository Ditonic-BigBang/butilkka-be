package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionMapItem;
import bigbang.butilkka_be.region.dto.RegionMapResponse;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionMapService {

    private final CommercialStatsQueryService commercialStatsQueryService;
    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;

    public RegionMapResponse getMap(String quarterParam) {
        List<CommercialStats> statsList;
        String quarterLabel;

        if (quarterParam == null || quarterParam.isBlank()) {
            statsList = commercialStatsQueryService.latestPerRegion();
            quarterLabel = resolveLatestLabel(statsList);
        } else {
            int[] parsed = CommercialStatsQueryService.parseQuarterLabel(quarterParam)
                    .orElseThrow(() -> AppException.badRequest("지원하지 않는 지표입니다."));
            statsList = commercialStatsQueryService.forQuarter(parsed[0], parsed[1]);
            quarterLabel = quarterParam;
        }

        List<RegionMapItem> items = statsList.stream()
                .map(this::toMapItem)
                .toList();

        return new RegionMapResponse(quarterLabel, items);
    }

    private RegionMapItem toMapItem(CommercialStats stats) {
        Region region = regionRepository.findById(stats.getRegionCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        District district = districtRepository.findById(region.getDistrictCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 자치구코드입니다."));
        return new RegionMapItem(region.getRegionCode(), region.getRegionName(), district.getDistrictName(), stats.getDeclineGrade());
    }

    private String resolveLatestLabel(List<CommercialStats> statsList) {
        return statsList.stream()
                .max(Comparator.comparingInt(CommercialStats::getYear).thenComparingInt(CommercialStats::getQuarter))
                .map(s -> s.getYear() + "Q" + s.getQuarter())
                .orElse(null);
    }
}
