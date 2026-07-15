package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
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
    private final RegionRepository regionRepository;
    private final DistrictRepository districtRepository;
    private final CommercialStatsQueryService commercialStatsQueryService;

    @Transactional
    public FavoriteItem add(Long userId, String regionCode) {
        if (userInterestRegionRepository.findByUserId(userId).size() >= MAX_FAVORITES) {
            throw AppException.conflict("최대 4개까지만 등록 가능합니다.");
        }
        if (userInterestRegionRepository.findByUserIdAndRegionCode(userId, regionCode).isPresent()) {
            throw AppException.conflict("이미 등록된 관심 상권입니다.");
        }

        Region region = regionRepository.findById(regionCode)
                .orElseThrow(() -> AppException.badRequest("존재하지 않는 상권코드입니다."));
        District district = districtRepository.findById(region.getDistrictCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 자치구코드입니다."));

        int nextSortOrder = userInterestRegionRepository.findByUserId(userId).size() + 1;
        UserInterestRegion favorite = UserInterestRegion.create(userId, regionCode, region.getRegionName(), nextSortOrder);
        userInterestRegionRepository.save(favorite);

        return new FavoriteItem(region.getRegionCode(), region.getRegionName(), district.getDistrictName(), null);
    }

    public List<FavoriteItem> list(Long userId) {
        return userInterestRegionRepository.findByUserId(userId).stream()
                .map(this::toFavoriteItem)
                .toList();
    }

    @Transactional
    public void remove(Long userId, String regionCode) {
        UserInterestRegion favorite = userInterestRegionRepository.findByUserIdAndRegionCode(userId, regionCode)
                .orElseThrow(() -> AppException.notFound("등록되지 않은 관심 상권입니다."));
        userInterestRegionRepository.delete(favorite);
    }

    private FavoriteItem toFavoriteItem(UserInterestRegion favorite) {
        Region region = regionRepository.findById(favorite.getRegionCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다."));
        District district = districtRepository.findById(region.getDistrictCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 자치구코드입니다."));
        String grade = commercialStatsQueryService.latestForRegion(region.getRegionCode())
                .map(bigbang.butilkka_be.stats.CommercialStats::getDeclineGrade)
                .orElse(null);
        return new FavoriteItem(region.getRegionCode(), region.getRegionName(), district.getDistrictName(), grade);
    }
}
