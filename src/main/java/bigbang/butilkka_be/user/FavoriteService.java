package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.stats.DistrictStats;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
import bigbang.butilkka_be.user.dto.FavoriteItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FavoriteService {

    private static final int MAX_FAVORITES = 4;

    private final UserInterestRegionRepository userInterestRegionRepository;
    private final DistrictRepository districtRepository;
    private final RegionRepository regionRepository;
    private final DistrictStatsQueryService districtStatsQueryService;

    @Transactional
    public FavoriteItem add(Long userId, String code) {
        if (code == null || code.isBlank()) {
            throw AppException.badRequest("지역 코드가 필요합니다.");
        }
        // 10자리면 앞 5자리 추출, 5자리면 그대로 사용 (구 코드)
        String districtCode = code.length() >= 10 ? code.substring(0, 5) : code;

        if (userInterestRegionRepository.findByUserId(userId).size() >= MAX_FAVORITES) {
            throw AppException.conflict("최대 4개까지만 등록 가능합니다.");
        }
        // 해당 구에 이미 등록된 관심 지역이 있는지 확인 (10자리 코드가 해당 구로 시작하는지)
        if (userInterestRegionRepository.findByUserIdAndRegionCodeStartingWith(userId, districtCode).isPresent()) {
            throw AppException.conflict("이미 등록된 관심 지역입니다.");
        }

        District district = districtRepository.findById(districtCode)
                .orElseThrow(() -> AppException.badRequest("존재하지 않는 구코드입니다."));

        // FK 만족을 위해 해당 구의 첫 번째 10자리 region_code를 찾아서 저장
        Region region = regionRepository.findFirstByDistrictCode(districtCode)
                .orElseThrow(() -> AppException.badRequest("해당 구에 등록된 행정동이 없습니다."));
        String regionCode = region.getRegionCode();

        int nextSortOrder = userInterestRegionRepository.findByUserId(userId).size() + 1;
        UserInterestRegion favorite = UserInterestRegion.create(userId, regionCode, district.getDistrictName(), nextSortOrder);
        userInterestRegionRepository.save(favorite);

        String grade = getLatestGrade(districtCode);
        return new FavoriteItem(districtCode, district.getDistrictName(), district.getDistrictName(), grade);
    }

    public List<FavoriteItem> list(Long userId) {
        return userInterestRegionRepository.findByUserId(userId).stream()
                .map(this::toFavoriteItem)
                .toList();
    }

    @Transactional
    public void remove(Long userId, String code) {
        // 10자리면 앞 5자리 추출 (구 코드)
        String districtCode = code.length() >= 10 ? code.substring(0, 5) : code;

        // 해당 구로 시작하는 10자리 코드로 저장된 즐겨찾기 찾기
        UserInterestRegion favorite = userInterestRegionRepository.findByUserIdAndRegionCodeStartingWith(userId, districtCode)
                .orElseThrow(() -> AppException.notFound("등록되지 않은 관심 지역입니다."));
        userInterestRegionRepository.delete(favorite);
    }

    private FavoriteItem toFavoriteItem(UserInterestRegion favorite) {
        String districtCode = favorite.getRegionCode();
        // 기존에 10자리로 저장된 경우 앞 5자리 추출
        if (districtCode.length() >= 10) {
            districtCode = districtCode.substring(0, 5);
        }

        District district = districtRepository.findById(districtCode)
                .orElse(null);
        String districtName = district != null ? district.getDistrictName() : favorite.getAlias();
        String grade = getLatestGrade(districtCode);

        return new FavoriteItem(districtCode, districtName, districtName, grade);
    }

    private String getLatestGrade(String districtCode) {
        List<DistrictStats> history = districtStatsQueryService.historyForDistrict(districtCode);
        if (history.isEmpty()) {
            return null;
        }
        return history.get(history.size() - 1).getDeclineGrade();
    }
}
