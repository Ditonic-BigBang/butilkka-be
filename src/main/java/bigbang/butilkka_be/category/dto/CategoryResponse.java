package bigbang.butilkka_be.category.dto;

import bigbang.butilkka_be.category.Category;

public record CategoryResponse(String categoryCode, String categoryName) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getCategoryCode(), category.getCategoryName());
    }
}
