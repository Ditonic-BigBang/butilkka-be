package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.stats.DistrictStats;
import bigbang.butilkka_be.stats.DistrictStatsQueryService;
import bigbang.butilkka_be.user.dto.FavoriteItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @Mock
    private UserInterestRegionRepository userInterestRegionRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private DistrictStatsQueryService districtStatsQueryService;

    private FavoriteService favoriteService;

    @BeforeEach
    void setUp() {
        favoriteService = new FavoriteService(userInterestRegionRepository, districtRepository, districtStatsQueryService);
    }

    @Test
    void add_withValidDistrict_createsFavorite() {
        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("강남구");

        when(userInterestRegionRepository.findByUserId(1L)).thenReturn(List.of());
        when(userInterestRegionRepository.findByUserIdAndRegionCode(1L, "11680")).thenReturn(Optional.empty());
        when(districtRepository.findById("11680")).thenReturn(Optional.of(district));

        DistrictStats stats = mock(DistrictStats.class);
        when(stats.getDeclineGrade()).thenReturn("A");
        when(districtStatsQueryService.historyForDistrict("11680")).thenReturn(List.of(stats));

        FavoriteItem result = favoriteService.add(1L, "1168064000");  // 10자리 코드 → 앞 5자리 추출

        assertThat(result.regionName()).isEqualTo("강남구");
        assertThat(result.district()).isEqualTo("강남구");
        assertThat(result.grade()).isEqualTo("A");
        verify(userInterestRegionRepository, times(1)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void add_withUnknownDistrict_throwsBadRequest() {
        when(userInterestRegionRepository.findByUserId(1L)).thenReturn(List.of());
        when(userInterestRegionRepository.findByUserIdAndRegionCode(1L, "99999")).thenReturn(Optional.empty());
        when(districtRepository.findById("99999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.add(1L, "9999900000"))  // 10자리 → 앞 5자리 추출
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void add_whenAlreadyFourFavorites_throwsConflict() {
        UserInterestRegion existing1 = UserInterestRegion.create(1L, "11110", null, 1);
        UserInterestRegion existing2 = UserInterestRegion.create(1L, "11140", null, 2);
        UserInterestRegion existing3 = UserInterestRegion.create(1L, "11170", null, 3);
        UserInterestRegion existing4 = UserInterestRegion.create(1L, "11200", null, 4);
        when(userInterestRegionRepository.findByUserId(1L)).thenReturn(List.of(existing1, existing2, existing3, existing4));

        assertThatThrownBy(() -> favoriteService.add(1L, "1168064000"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void add_whenAlreadyFavorited_throwsConflict() {
        when(userInterestRegionRepository.findByUserId(1L)).thenReturn(List.of());
        when(userInterestRegionRepository.findByUserIdAndRegionCode(1L, "11680"))
                .thenReturn(Optional.of(UserInterestRegion.create(1L, "11680", null, 1)));

        assertThatThrownBy(() -> favoriteService.add(1L, "1168064000"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void list_returnsFavoritesWithLatestGrade() {
        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("강남구");

        DistrictStats stats = mock(DistrictStats.class);
        when(stats.getDeclineGrade()).thenReturn("A");

        UserInterestRegion favorite = UserInterestRegion.create(1L, "11680", "강남구", 1);
        when(userInterestRegionRepository.findByUserId(1L)).thenReturn(List.of(favorite));
        when(districtRepository.findById("11680")).thenReturn(Optional.of(district));
        when(districtStatsQueryService.historyForDistrict("11680")).thenReturn(List.of(stats));

        List<FavoriteItem> result = favoriteService.list(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).grade()).isEqualTo("A");
    }

    @Test
    void remove_withRegisteredFavorite_deletesIt() {
        UserInterestRegion favorite = UserInterestRegion.create(1L, "11680", null, 1);
        when(userInterestRegionRepository.findByUserIdAndRegionCode(1L, "11680")).thenReturn(Optional.of(favorite));

        favoriteService.remove(1L, "1168064000");  // 10자리 → 앞 5자리

        verify(userInterestRegionRepository, times(1)).delete(favorite);
    }

    @Test
    void remove_withUnregisteredFavorite_throwsNotFound() {
        when(userInterestRegionRepository.findByUserIdAndRegionCode(1L, "11680")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.remove(1L, "1168064000"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
