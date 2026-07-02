package bigbang.butilkka_be.user;

import bigbang.butilkka_be.category.Category;
import bigbang.butilkka_be.category.CategoryRepository;
import bigbang.butilkka_be.common.exception.AppException;
import bigbang.butilkka_be.region.Region;
import bigbang.butilkka_be.region.RegionRepository;
import bigbang.butilkka_be.user.dto.StoreResponse;
import bigbang.butilkka_be.user.dto.StoreUpdateRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RegionRepository regionRepository;
    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void updateStore_withValidCodes_updatesUserAndReturnsMergedResponse() {
        User user = User.create(1L, "김민수");
        Region region = mock(Region.class);
        when(region.getRegionName()).thenReturn("역삼1동");
        Category category = mock(Category.class);
        when(category.getCategoryName()).thenReturn("한식음식점");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(region));
        when(categoryRepository.findById("CS100001")).thenReturn(Optional.of(category));

        StoreUpdateRequest request = new StoreUpdateRequest(
                "1168064000", "CS100001", 37.5, 127.03, "민수네 한식당", LocalDate.of(2022, 3, 15));

        StoreResponse response = userService.updateStore(1L, request);

        assertThat(response.regionName()).isEqualTo("역삼1동");
        assertThat(response.categoryName()).isEqualTo("한식음식점");
        assertThat(user.isOnboarded()).isTrue();
    }

    @Test
    void updateStore_withUnknownRegionCode_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.create(1L, "김민수")));
        when(regionRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        StoreUpdateRequest request = new StoreUpdateRequest(
                "UNKNOWN", "CS100001", 37.5, 127.03, "가게", LocalDate.of(2022, 3, 15));

        assertThatThrownBy(() -> userService.updateStore(1L, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateStore_withUnknownCategoryCode_throwsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.create(1L, "김민수")));
        when(regionRepository.findById("1168064000")).thenReturn(Optional.of(mock(Region.class)));
        when(categoryRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

        StoreUpdateRequest request = new StoreUpdateRequest(
                "1168064000", "UNKNOWN", 37.5, 127.03, "가게", LocalDate.of(2022, 3, 15));

        assertThatThrownBy(() -> userService.updateStore(1L, request))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
    }
}
