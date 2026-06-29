package bigbang.butilkka_be.common.exception;

import java.util.List;

public record ErrorResponse(int status, String message, List<String> errors) {

    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, List.of());
    }
}
