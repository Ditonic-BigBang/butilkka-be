package bigbang.butilkka_be.user;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.user.dto.StoreResponse;
import bigbang.butilkka_be.user.dto.StoreUpdateRequest;
import bigbang.butilkka_be.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;

    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다"));
        return UserResponse.of(user, buildStoreInfo(user));
    }

    @Transactional
    public StoreResponse updateStore(Long userId, StoreUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다"));

        Region region = regionRepository.findById(request.regionCode())
                .orElseThrow(() -> AppException.badRequest("존재하지 않는 상권코드 또는 업종 코드입니다"));
        Category category = categoryRepository.findById(request.categoryCode())
                .orElseThrow(() -> AppException.badRequest("존재하지 않는 상권코드 또는 업종 코드입니다"));

        user.updateStore(
                request.regionCode(),
                request.categoryCode(),
                request.lat(),
                request.lng(),
                request.storeName(),
                request.storeOpenDate());

        return StoreResponse.of(user, region.getRegionName(), category.getCategoryName());
    }

    private UserResponse.StoreInfo buildStoreInfo(User user) {
        if (user.getStoreRegion() == null) {
            return null;
        }
        Region region = regionRepository.findById(user.getStoreRegion())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다"));
        Category category = categoryRepository.findById(user.getCategoryCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 업종코드입니다"));
        return new UserResponse.StoreInfo(
                user.getStoreRegion(), region.getRegionName(),
                user.getCategoryCode(), category.getCategoryName(),
                user.getStoreLat(), user.getStoreLng());
    }
}
