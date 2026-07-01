package bigbang.butilkka_be.common.exception;

import bigbang.butilkka_be.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void missingRequestParameter_returnsBadRequest() {
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("lat", "double");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingParams(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().status()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void typeMismatchedRequestParameter_returnsBadRequest() throws NoSuchMethodException {
        MethodParameter methodParameter = new MethodParameter(
                DummyController.class.getMethod("dummy", double.class), 0);
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", double.class, "lat", methodParameter, new NumberFormatException());

        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().status()).isEqualTo("BAD_REQUEST");
    }

    static class DummyController {
        public void dummy(double lat) {
        }
    }
}
