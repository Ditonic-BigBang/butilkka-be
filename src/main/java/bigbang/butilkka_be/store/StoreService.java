package bigbang.butilkka_be.store;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.store.dto.CreateStoreRequest;
import bigbang.butilkka_be.store.dto.StoreListResponse;
import bigbang.butilkka_be.store.dto.UpdateStoreRequest;
import bigbang.butilkka_be.user.User;
import bigbang.butilkka_be.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;

    public List<StoreListResponse> getStores(Long userId) {
        List<Store> stores = storeRepository.findByUserIdOrderByIsPrimaryDescCreatedAtDesc(userId);
        return stores.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public StoreListResponse createStore(Long userId, CreateStoreRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("사용자를 찾을 수 없습니다"));

        regionRepository.findById(request.regionCode())
                .orElseThrow(() -> AppException.badRequest("존재하지 않는 상권코드입니다"));
        categoryRepository.findById(request.categoryCode())
                .orElseThrow(() -> AppException.badRequest("존재하지 않는 업종코드입니다"));

        // 첫 가게면 대표로 설정
        boolean isFirst = storeRepository.countByUserId(userId) == 0;

        Store store = Store.create(
                user,
                request.storeName(),
                request.storeAddress(),
                request.storeOpenDate(),
                request.regionCode(),
                request.categoryCode(),
                request.lat(),
                request.lng(),
                isFirst
        );

        storeRepository.save(store);

        // 첫 가게 등록 시 온보딩 완료 처리
        if (isFirst && !user.isOnboarded()) {
            user.completeOnboarding();
        }

        return toResponse(store);
    }

    @Transactional
    public StoreListResponse updateStore(Long userId, Long storeId, UpdateStoreRequest request) {
        Store store = storeRepository.findByIdAndUserId(storeId, userId)
                .orElseThrow(() -> AppException.notFound("가게를 찾을 수 없습니다"));

        if (request.regionCode() != null) {
            regionRepository.findById(request.regionCode())
                    .orElseThrow(() -> AppException.badRequest("존재하지 않는 상권코드입니다"));
        }
        if (request.categoryCode() != null) {
            categoryRepository.findById(request.categoryCode())
                    .orElseThrow(() -> AppException.badRequest("존재하지 않는 업종코드입니다"));
        }

        // 대표 지정 처리
        if (Boolean.TRUE.equals(request.isPrimary()) && !store.isPrimary()) {
            storeRepository.clearPrimaryByUserId(userId);
            store.setPrimary(true);
        }

        // 가게 정보 업데이트
        store.update(
                request.storeName() != null ? request.storeName() : store.getStoreName(),
                request.storeAddress() != null ? request.storeAddress() : store.getStoreAddress(),
                request.storeOpenDate() != null ? request.storeOpenDate() : store.getStoreOpenDate(),
                request.regionCode() != null ? request.regionCode() : store.getRegionCode(),
                request.categoryCode() != null ? request.categoryCode() : store.getCategoryCode(),
                request.lat() != null ? request.lat() : store.getLat(),
                request.lng() != null ? request.lng() : store.getLng()
        );

        return toResponse(store);
    }

    @Transactional
    public void deleteStore(Long userId, Long storeId) {
        Store store = storeRepository.findByIdAndUserId(storeId, userId)
                .orElseThrow(() -> AppException.notFound("가게를 찾을 수 없습니다"));

        boolean wasPrimary = store.isPrimary();
        storeRepository.delete(store);

        // 대표 가게 삭제 시 다음 가게를 대표로 승격
        if (wasPrimary) {
            List<Store> remaining = storeRepository.findByUserIdOrderByIsPrimaryDescCreatedAtDesc(userId);
            if (!remaining.isEmpty()) {
                remaining.get(0).setPrimary(true);
            }
        }
    }

    private StoreListResponse toResponse(Store store) {
        Region region = regionRepository.findById(store.getRegionCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 상권코드입니다"));
        Category category = categoryRepository.findById(store.getCategoryCode())
                .orElseThrow(() -> AppException.notFound("존재하지 않는 업종코드입니다"));
        return StoreListResponse.of(store, region.getRegionName(), category.getCategoryName());
    }
}
