package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionSearchItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RegionSearchService {

    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;

    public List<RegionSearchItem> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw AppException.badRequest("검색어를 입력해주세요.");
        }

        // regionName 또는 districtName으로 검색
        return regionRepository.searchByKeyword(keyword.trim()).stream()
                .map(this::toSearchItem)
                .toList();
    }

    private RegionSearchItem toSearchItem(Region region) {
        District district = districtRepository.findById(region.getDistrictCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 자치구코드입니다."));
        return new RegionSearchItem(region.getRegionCode(), region.getRegionName(), district.getDistrictName());
    }
}
