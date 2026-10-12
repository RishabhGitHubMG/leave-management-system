package com.settribe.leave.exception;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Standardized JSON error response structure returned across all API endpoints.
 * 
 * @param status    HTTP status code (e.g. 400, 401, 403, 404, 409).
 * @param error     HTTP status reason phrase (e.g. "Bad Request", "Unauthorized").
 * @param message   Human-readable summary of the error.
 * @param errors    Map of field-specific validation errors (empty map if none).
 * @param timestamp Exact timestamp when the error occurred.
 */
public record ApiError(int status, String error, String message,
                       Map<String, String> errors, LocalDateTime timestamp) {}

