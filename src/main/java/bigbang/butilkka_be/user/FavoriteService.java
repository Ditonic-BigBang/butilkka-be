package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
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
    private final DistrictStatsQueryService districtStatsQueryService;

    @Transactional
    public FavoriteItem add(Long userId, String code) {
        // 10자리면 앞 5자리 추출, 5자리면 그대로 사용
        String districtCode = code.length() >= 10 ? code.substring(0, 5) : code;

        if (userInterestRegionRepository.findByUserId(userId).size() >= MAX_FAVORITES) {
            throw AppException.conflict("최대 4개까지만 등록 가능합니다.");
        }
        if (userInterestRegionRepository.findByUserIdAndRegionCode(userId, districtCode).isPresent()) {
            throw AppException.conflict("이미 등록된 관심 지역입니다.");
        }

        District district = districtRepository.findById(districtCode)
                .orElseThrow(() -> AppException.badRequest("존재하지 않는 구코드입니다."));

        int nextSortOrder = userInterestRegionRepository.findByUserId(userId).size() + 1;
        UserInterestRegion favorite = UserInterestRegion.create(userId, districtCode, district.getDistrictName(), nextSortOrder);
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
        // 10자리면 앞 5자리 추출
        String districtCode = code.length() >= 10 ? code.substring(0, 5) : code;

        UserInterestRegion favorite = userInterestRegionRepository.findByUserIdAndRegionCode(userId, districtCode)
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
