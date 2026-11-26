package dev.ltp.control.config;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Error bodies of the public API: RFC 9457 {@code ProblemDetail}. Validation errors add an
 * {@code errors} list that names each wrong field, so the UI can show the message next to it.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    /** One wrong request field. */
    public record FieldProblem(String field, String message) {}

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApiException(ApiException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        if (!e.fieldErrors().isEmpty()) {
            problem.setProperty("errors", toList(e.fieldErrors()));
        }
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = e.getBody();
        problem.setDetail("Validation failed");
        problem.setProperty(
                "errors",
                e.getBindingResult().getFieldErrors().stream()
                        .map(error -> new FieldProblem(error.getField(), message(error)))
                        .toList());
        return handleExceptionInternal(e, problem, headers, status, request);
    }

    private static String message(FieldError error) {
        return error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
    }

    private static List<FieldProblem> toList(Map<String, String> fieldErrors) {
        return fieldErrors.entrySet().stream()
                .map(entry -> new FieldProblem(entry.getKey(), entry.getValue()))
                .toList();
    }
}
