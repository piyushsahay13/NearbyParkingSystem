package com.wego.parkingsystem.exception;

import com.wego.parkingsystem.dto.ApiError;
import com.wego.parkingsystem.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Centralized exception handler mapping all application and system exceptions
 * to standardized {@link ApiResponse} error payloads with correct HTTP status codes.
 *
 * <p>Mapping summary:
 * <ul>
 *   <li>{@link ValidationException}                    → HTTP 400</li>
 *   <li>{@link BusinessException} (404/409/422)        → HTTP varies</li>
 *   <li>{@link SynchronizationException}               → HTTP 503</li>
 *   <li>{@link PartnerApiException}                    → HTTP 502/504</li>
 *   <li>{@link DatabaseException}/{@link DatasetException}     → HTTP 500</li>
 *   <li>{@link InternalServerException}                → HTTP 500</li>
 *   <li>Spring validation exceptions                   → HTTP 400</li>
 *   <li>Spring method/media/parse exceptions           → HTTP 405/415/400</li>
 *   <li>All other uncaught exceptions                  → HTTP 500</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ─── Application Domain Exceptions ───────────────────────────────────────

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            ValidationException ex, HttpServletRequest request) {
        log.debug("Validation error [{}]: {}", ex.getErrorCode().getCode(), ex.getUserMessage());
        return buildError(ex.getHttpStatus(), ex.getErrorCode().getCode(),
                "VALIDATION_ERROR", ex.getUserMessage(), null, generateTraceId());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(
            BusinessException ex, HttpServletRequest request) {
        log.debug("Business error [{}]: {}", ex.getErrorCode().getCode(), ex.getUserMessage());
        return buildError(ex.getHttpStatus(), ex.getErrorCode().getCode(),
                "BUSINESS_ERROR", ex.getUserMessage(), null, generateTraceId());
    }

    @ExceptionHandler(PartnerApiException.class)
    public ResponseEntity<ApiResponse<Void>> handlePartnerApi(
            PartnerApiException ex, HttpServletRequest request) {
        log.error("Partner API error [{}]: {}", ex.getErrorCode().getCode(), ex.getUserMessage());
        String type = ex.getHttpStatus() == 504 ? "PARTNER_TIMEOUT" : "PARTNER_API_ERROR";
        return buildError(ex.getHttpStatus(), ex.getErrorCode().getCode(),
                type, ex.getUserMessage(), null, generateTraceId());
    }

    @ExceptionHandler(SynchronizationException.class)
    public ResponseEntity<ApiResponse<Void>> handleSynchronization(
            SynchronizationException ex, HttpServletRequest request) {
        log.warn("Synchronization error [{}]: {}", ex.getErrorCode().getCode(), ex.getUserMessage());
        return buildError(ex.getHttpStatus(), ex.getErrorCode().getCode(),
                "SERVICE_INITIALIZATION", ex.getUserMessage(), null, generateTraceId());
    }

    @ExceptionHandler({DatabaseException.class, DatasetException.class})
    public ResponseEntity<ApiResponse<Void>> handleDatabase(
            ApplicationException ex, HttpServletRequest request) {
        log.error("Database/Dataset error [{}]: {}", ex.getErrorCode().getCode(), ex.getUserMessage(), ex);
        return buildError(500, ex.getErrorCode().getCode(),
                "INTERNAL_SERVER_ERROR", ex.getUserMessage(), null, generateTraceId());
    }

    @ExceptionHandler(InternalServerException.class)
    public ResponseEntity<ApiResponse<Void>> handleInternal(
            InternalServerException ex, HttpServletRequest request) {
        log.error("Internal error [{}]: {}", ex.getErrorCode().getCode(), ex.getUserMessage(), ex);
        return buildError(500, ex.getErrorCode().getCode(),
                "INTERNAL_SERVER_ERROR", ex.getUserMessage(), null, generateTraceId());
    }

    // ─── Spring Validation Exceptions ────────────────────────────────────────

    /**
     * Handles @RequestParam constraint violations (e.g. @Min, @Max, @NotNull).
     * Returns single error for 1 violation, multi-error (CP-400-999) for multiple.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<ConstraintViolation<?>> violations = List.copyOf(ex.getConstraintViolations());
        String traceId = generateTraceId();

        if (violations.size() == 1) {
            ConstraintViolation<?> v = violations.get(0);
            String field = extractField(v.getPropertyPath().toString());
            return buildError(400, ErrorCode.CP_400_999.getCode(),
                    "VALIDATION_ERROR", v.getMessage(), field, traceId);
        }

        List<ApiError.FieldError> details = violations.stream()
                .map(v -> ApiError.FieldError.builder()
                        .field(extractField(v.getPropertyPath().toString()))
                        .message(v.getMessage())
                        .build())
                .collect(Collectors.toList());

        return buildMultiError(400, ErrorCode.CP_400_999.getCode(),
                "VALIDATION_ERROR", "Request validation failed.", details, traceId);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        return buildError(400, ErrorCode.CP_400_011.getCode(),
                "VALIDATION_ERROR",
                "Required parameter '" + ex.getParameterName() + "' is missing.",
                ex.getParameterName(), generateTraceId());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return buildError(400, ErrorCode.CP_400_010.getCode(),
                "VALIDATION_ERROR",
                "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue(),
                ex.getName(), generateTraceId());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        return buildError(400, ErrorCode.CP_400_010.getCode(),
                "VALIDATION_ERROR", "Request body is malformed or unreadable.",
                null, generateTraceId());
    }

    // ─── HTTP Method / Media Type Exceptions ─────────────────────────────────

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return buildError(405, "CP-405-001", "METHOD_NOT_ALLOWED",
                "HTTP method '" + ex.getMethod() + "' is not supported for this endpoint.",
                null, generateTraceId());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMedia(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return buildError(415, "CP-415-001", "UNSUPPORTED_MEDIA_TYPE",
                "Content-Type '" + ex.getContentType() + "' is not supported.",
                null, generateTraceId());
    }

    // ─── Catch-All ────────────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {} {}: {}", request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);
        return buildError(500, ErrorCode.CP_500_900.getCode(),
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred while processing the request.",
                null, generateTraceId());
    }

    // ─── Builder helpers ─────────────────────────────────────────────────────

    private ResponseEntity<ApiResponse<Void>> buildError(
            int status, String code, String type, String message, String field, String traceId) {

        ApiError apiError = ApiError.builder()
                .status(status)
                .code(code)
                .type(type)
                .message(message)
                .field(field)
                .build();

        return ResponseEntity.status(status)
                .body(ApiResponse.error(apiError, traceId));
    }

    private ResponseEntity<ApiResponse<Void>> buildMultiError(
            int status, String code, String type, String message,
            List<ApiError.FieldError> details, String traceId) {

        ApiError apiError = ApiError.builder()
                .status(status)
                .code(code)
                .type(type)
                .message(message)
                .details(details)
                .build();

        return ResponseEntity.status(status)
                .body(ApiResponse.error(apiError, traceId));
    }

    private static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /** Extracts simple field name from dot-notation constraint path (e.g. "searchNearby.latitude" → "latitude"). */
    private static String extractField(String propertyPath) {
        String[] parts = propertyPath.split("\\.");
        return parts[parts.length - 1];
    }
}
