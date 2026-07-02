package bigbang.butilkka_be.common.exception;

import bigbang.butilkka_be.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

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

    @Test
    void appException_mapsToItsOwnHttpStatusAndMessage() {
        AppException ex = AppException.conflict("이미 존재합니다");

        ResponseEntity<ApiResponse<Void>> response = handler.handleAppException(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().status()).isEqualTo("CONFLICT");
        assertThat(response.getBody().message()).isEqualTo("이미 존재합니다");
    }

    @Test
    void methodArgumentNotValid_returnsBadRequestWithFieldErrors() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(new FieldError("target", "email", "must not be blank"));
        MethodParameter methodParameter = new MethodParameter(
                DummyController.class.getMethod("dummy", double.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ApiResponse<List<String>>> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().data()).containsExactly("email: must not be blank");
    }

    @Test
    void httpMessageNotReadable_returnsBadRequest() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("broken json", (java.io.IOException) null, null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleNotReadable(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().status()).isEqualTo("BAD_REQUEST");
    }

    @Test
    void unhandledException_returnsInternalServerError() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleException(new RuntimeException("boom"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().status()).isEqualTo("INTERNAL_SERVER_ERROR");
    }

    static class DummyController {
        public void dummy(double lat) {
        }
    }
}
