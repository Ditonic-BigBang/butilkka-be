package bigbang.butilkka_be.category;

import bigbang.butilkka_be.category.dto.CategoryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Test
    void getCategories_returnsAllCategoriesAsResponse() {
        Category korean = mock(Category.class);
        when(korean.getCategoryCode()).thenReturn("CS100001");
        when(korean.getCategoryName()).thenReturn("한식음식점");
        Category cafe = mock(Category.class);
        when(cafe.getCategoryCode()).thenReturn("CS100006");
        when(cafe.getCategoryName()).thenReturn("커피전문점");
        when(categoryRepository.findAll()).thenReturn(List.of(korean, cafe));

        CategoryService categoryService = new CategoryService(categoryRepository);
        List<CategoryResponse> result = categoryService.getCategories();

        assertThat(result).containsExactly(
                new CategoryResponse("CS100001", "한식음식점"),
                new CategoryResponse("CS100006", "커피전문점"));
    }
}
