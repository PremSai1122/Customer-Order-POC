package com.poc.order.exception;

import org.springframework.http.HttpStatus;

/**
 * Single exception type for every business-rule failure in this service.
 * Replaces the old one-class-per-entity approach (OrderNotFoundException)
 * - the HTTP status travels with the exception instead of being re-derived
 * per @ExceptionHandler method.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
