package bigbang.butilkka_be.user;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.store.Store;
import bigbang.butilkka_be.store.StoreRepository;
import bigbang.butilkka_be.user.dto.NotificationSettingsResponse;
import bigbang.butilkka_be.user.dto.NotificationSettingsUpdateRequest;
import bigbang.butilkka_be.user.dto.StoreResponse;
import bigbang.butilkka_be.user.dto.StoreUpdateRequest;
import bigbang.butilkka_be.user.dto.UserResponse;
import bigbang.butilkka_be.user.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final StoreRepository storeRepository;

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
                .orElseThrow(() -> AppException.badRequest("존재하지 않는 상권코드입니다"));
        Category category = categoryRepository.findById(request.categoryCode())
                .orElseThrow(() -> AppException.badRequest("존재하지 않는 업종코드입니다"));

        // 기존 users 테이블도 업데이트 (하위 호환)
        user.updateStore(
                request.regionCode(),
                request.categoryCode(),
                request.lat(),
                request.lng(),
                request.storeName(),
                request.storeAddress(),
                request.storeOpenDate());

        // stores 테이블에도 저장 (첫 가게 = 대표)
        boolean isFirst = storeRepository.countByUserId(userId) == 0;
        if (isFirst) {
            Store store = Store.create(
                    user,
                    request.storeName(),
                    request.storeAddress(),
                    request.storeOpenDate(),
                    request.regionCode(),
                    request.categoryCode(),
                    request.lat(),
                    request.lng(),
                    true
            );
            storeRepository.save(store);
        }

        return StoreResponse.of(user, region.getRegionName(), category.getCategoryName());
    }

    @Transactional
    public UserResponse updateProfile(Long userId, UserUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다"));

        String regionCode = null;
        String categoryCode = null;
        Double lat = null;
        Double lng = null;
        if (request.store() != null) {
            UserUpdateRequest.StoreUpdatePartial store = request.store();
            regionRepository.findById(store.regionCode())
                    .orElseThrow(() -> AppException.badRequest("존재하지 않는 상권코드 또는 업종 코드입니다"));
            categoryRepository.findById(store.categoryCode())
                    .orElseThrow(() -> AppException.badRequest("존재하지 않는 상권코드 또는 업종 코드입니다"));
            regionCode = store.regionCode();
            categoryCode = store.categoryCode();
            lat = store.lat();
            lng = store.lng();
        }

        user.updateProfile(request.name(), regionCode, categoryCode, lat, lng);

        return UserResponse.of(user, buildStoreInfo(user));
    }

    public NotificationSettingsResponse getNotificationSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다"));
        return NotificationSettingsResponse.from(user);
    }

    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(Long userId, NotificationSettingsUpdateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다"));
        user.updateNotificationSettings(request.smsAlert(), request.autoReport(), request.urgentAlert());
        return NotificationSettingsResponse.from(user);
    }

    private UserResponse.StoreInfo buildStoreInfo(User user) {
        // 대표 가게 조회 (stores 테이블 우선)
        Optional<Store> primaryStore = storeRepository.findByUserIdAndIsPrimaryTrue(user.getId());

        if (primaryStore.isPresent()) {
            Store store = primaryStore.get();
            Region region = regionRepository.findById(store.getRegionCode())
                    .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다"));
            Category category = categoryRepository.findById(store.getCategoryCode())
                    .orElseThrow(() -> AppException.notFound("존재하지 않는 업종코드입니다"));
            return new UserResponse.StoreInfo(
                    store.getRegionCode(), region.getRegionName(),
                    store.getCategoryCode(), category.getCategoryName(),
                    store.getLat(), store.getLng(),
                    store.getStoreName(), store.getStoreAddress(), store.getStoreOpenDate());
        }

        // 하위 호환: users 테이블의 가게 정보
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
                user.getStoreLat(), user.getStoreLng(),
                user.getStoreName(), user.getStoreAddress(), user.getStoreOpenDate());
    }
}
