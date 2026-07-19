package bigbang.butilkka_be.region;

import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.dto.RegionSearchItem;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegionSearchServiceTest {

    @Mock
    private RegionRepository regionRepository;
    @Mock
    private DistrictRepository districtRepository;

    private RegionSearchService service;

    @BeforeEach
    void setUp() {
        service = new RegionSearchService(regionRepository, districtRepository);
    }

    @Test
    void search_withMatchingRegionName_returnsResultsWithDistrict() {
        Region region = mock(Region.class);
        when(region.getRegionCode()).thenReturn("1168064000");
        when(region.getRegionName()).thenReturn("역삼1동");
        when(region.getDistrictCode()).thenReturn("11680");
        when(regionRepository.searchByKeyword("역삼")).thenReturn(List.of(region));

        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("강남구");
        when(districtRepository.findById("11680")).thenReturn(Optional.of(district));

        List<RegionSearchItem> result = service.search("역삼");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).regionName()).isEqualTo("역삼1동");
        assertThat(result.get(0).district()).isEqualTo("강남구");
    }

    @Test
    void search_withMatchingDistrictName_returnsResults() {
        // "마" 검색 시 districtName "마포구"에 매칭되는 region 반환
        Region region = mock(Region.class);
        when(region.getRegionCode()).thenReturn("1144051000");
        when(region.getRegionName()).thenReturn("망원1동");
        when(region.getDistrictCode()).thenReturn("11440");
        when(regionRepository.searchByKeyword("마")).thenReturn(List.of(region));

        District district = mock(District.class);
        when(district.getDistrictName()).thenReturn("마포구");
        when(districtRepository.findById("11440")).thenReturn(Optional.of(district));

        List<RegionSearchItem> result = service.search("마");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).district()).isEqualTo("마포구");
    }

    @Test
    void search_withBlankKeyword_throwsBadRequest() {
        assertThatThrownBy(() -> service.search(" "))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void search_withNullKeyword_throwsBadRequest() {
        assertThatThrownBy(() -> service.search(null))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void search_withNoMatch_returnsEmptyList() {
        when(regionRepository.searchByKeyword("없는동")).thenReturn(List.of());

        List<RegionSearchItem> result = service.search("없는동");

        assertThat(result).isEmpty();
    }
}
