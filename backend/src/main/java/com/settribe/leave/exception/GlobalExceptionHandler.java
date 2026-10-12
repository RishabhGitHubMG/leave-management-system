package com.settribe.leave.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized exception handler for all REST controllers across the application.
 * 
 * Intercepts uncaught exceptions and transforms them into standardized JSON error responses (ApiError),
 * ensuring the client never receives raw Java stack traces or internal implementation details.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles custom business logic exceptions thrown with specific HTTP statuses.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return build(ex.getStatus(), ex.getMessage(), Map.of());
    }

    /**
     * Handles validation failures triggered by @Valid on request bodies.
     * Collects all field validation errors into a key-value map.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", errors);
    }

    /**
     * Handles unparseable JSON payloads (e.g., malformed syntax, wrong date format).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body", Map.of());
    }

    /**
     * Handles invalid query parameters or path variables (e.g., non-numeric ID passed to /{id}).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", Map.of());
    }

    /**
     * Handles missing required query parameters.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST, "Missing parameter '" + ex.getParameterName() + "'", Map.of());
    }

    /**
     * Handles database constraint violations (e.g., unique email violation or foreign key conflicts).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, "Data conflicts with existing records", Map.of());
    }

    /**
     * Handles access denied errors when a user lacks the required role.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", Map.of());
    }

    /**
     * Handles requests to non-existent endpoint URLs.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Resource not found", Map.of());
    }

    /**
     * Handles unsupported HTTP methods (e.g., sending a POST request to a GET-only endpoint).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", Map.of());
    }

    /**
     * Handles unsupported request content types (e.g., sending XML when JSON is expected).
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content type not supported", Map.of());
    }

    /**
     * Fallback catch-all handler for any unhandled or unexpected server exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        log.error("Unexpected error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong", Map.of());
    }

    /**
     * Helper to construct a standardized ResponseEntity<ApiError>.
     */
    private ResponseEntity<ApiError> build(HttpStatus status, String message, Map<String, String> errors) {
        ApiError body = new ApiError(status.value(), status.getReasonPhrase(), message, errors, LocalDateTime.now());
        return ResponseEntity.status(status).body(body);
    }
}

