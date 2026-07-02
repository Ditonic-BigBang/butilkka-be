package bigbang.butilkka_be.user;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.District;
import bigbang.butilkka_be.region.DistrictRepository;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.stats.CommercialStats;
import bigbang.butilkka_be.stats.CommercialStatsQueryService;
import bigbang.butilkka_be.user.dto.FavoriteItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
    private RegionRepository regionRepository;
    @Mock
    private DistrictRepository districtRepository;
    @Mock
    private CommercialStatsQueryService commercialStatsQueryService;

    @InjectMocks
    private FavoriteService favoriteService;

    @Test
    void add_withValidRegion_createsFavorite() {
        Region region = mock(Region.class);
        when(region.getRegionCode()).thenReturn("1168064000");
        when(region.getRegionName()).thenReturn("역삼1동");
        when(region.getDistrictCode()).thenReturn("11680");

        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("강남구");

        when(userInterestRegionRepository.findByUserId(1L)).thenReturn(List.of());
        when(userInterestRegionRepository.findByUserIdAndRegionCode(1L, "1168064000")).thenReturn(Optional.empty());
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(region));
        when(districtRepository.findById("11680")).thenReturn(Optional.of(district));

        FavoriteItem result = favoriteService.add(1L, "1168064000");

        assertThat(result.regionName()).isEqualTo("역삼1동");
        assertThat(result.district()).isEqualTo("강남구");
        verify(userInterestRegionRepository, times(1)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void add_withUnknownRegion_throwsBadRequest() {
        when(userInterestRegionRepository.findByUserId(1L)).thenReturn(List.of());
        when(userInterestRegionRepository.findByUserIdAndRegionCode(1L, "UNKNOWN")).thenReturn(Optional.empty());
        when(regionRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.add(1L, "UNKNOWN"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void add_whenAlreadyThreeFavorites_throwsConflict() {
        UserInterestRegion existing1 = UserInterestRegion.create(1L, "A", null, 1);
        UserInterestRegion existing2 = UserInterestRegion.create(1L, "B", null, 2);
        UserInterestRegion existing3 = UserInterestRegion.create(1L, "C", null, 3);
        when(userInterestRegionRepository.findByUserId(1L)).thenReturn(List.of(existing1, existing2, existing3));

        assertThatThrownBy(() -> favoriteService.add(1L, "1168064000"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void add_whenAlreadyFavorited_throwsConflict() {
        when(userInterestRegionRepository.findByUserId(1L)).thenReturn(List.of());
        when(userInterestRegionRepository.findByUserIdAndRegionCode(1L, "1168064000"))
                .thenReturn(Optional.of(UserInterestRegion.create(1L, "1168064000", null, 1)));

        assertThatThrownBy(() -> favoriteService.add(1L, "1168064000"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void list_returnsFavoritesWithLatestGrade() {
        Region region = mock(Region.class);
        when(region.getRegionCode()).thenReturn("1168064000");
        when(region.getRegionName()).thenReturn("역삼1동");
        when(region.getDistrictCode()).thenReturn("11680");

        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("강남구");

        CommercialStats stats = mock(CommercialStats.class);
        when(stats.getDeclineGrade()).thenReturn("A");

        UserInterestRegion favorite = UserInterestRegion.create(1L, "1168064000", null, 1);
        when(userInterestRegionRepository.findByUserId(1L)).thenReturn(List.of(favorite));
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(region));
        when(districtRepository.findById("11680")).thenReturn(Optional.of(district));
        when(commercialStatsQueryService.latestForRegion("1168064000")).thenReturn(Optional.of(stats));

        List<FavoriteItem> result = favoriteService.list(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).grade()).isEqualTo("A");
    }

    @Test
    void remove_withRegisteredFavorite_deletesIt() {
        UserInterestRegion favorite = UserInterestRegion.create(1L, "1168064000", null, 1);
        when(userInterestRegionRepository.findByUserIdAndRegionCode(1L, "1168064000")).thenReturn(Optional.of(favorite));

        favoriteService.remove(1L, "1168064000");

        verify(userInterestRegionRepository, times(1)).delete(favorite);
    }

    @Test
    void remove_withUnregisteredFavorite_throwsNotFound() {
        when(userInterestRegionRepository.findByUserIdAndRegionCode(1L, "1168064000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> favoriteService.remove(1L, "1168064000"))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
