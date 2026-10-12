package com.settribe.leave.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Custom runtime exception representing an explicit HTTP API error with status and message.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}

