package dev.ltp.control.config;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * A request the API refuses with a clear reason. {@link ApiExceptionHandler} turns it into a
 * {@code ProblemDetail}; {@code fieldErrors} names the request fields that were wrong.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final Map<String, String> fieldErrors;

    private ApiException(HttpStatus status, String detail, Map<String, String> fieldErrors) {
        super(detail);
        this.status = status;
        this.fieldErrors = Map.copyOf(fieldErrors);
    }

    public static ApiException badRequest(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Validation failed", Map.of(field, message));
    }

    public static ApiException unauthorized(String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, detail, Map.of());
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, detail, Map.of());
    }

    public static ApiException notFound(String detail) {
        return new ApiException(HttpStatus.NOT_FOUND, detail, Map.of());
    }

    public static ApiException conflict(String detail) {
        return new ApiException(HttpStatus.CONFLICT, detail, Map.of());
    }

    public HttpStatus status() {
        return status;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}
