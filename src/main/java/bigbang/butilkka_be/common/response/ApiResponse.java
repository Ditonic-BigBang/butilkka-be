package bigbang.butilkka_be.common.response;

public record ApiResponse<T>(int code, String status, String message, T data) {

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(200, "OK", message, data);
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>(201, "CREATED", message, data);
    }

    public static ApiResponse<Void> error(int code, String status, String message) {
        return new ApiResponse<>(code, status, message, null);
    }
}
