package com.poc.composite.exception;

import org.springframework.http.HttpStatus;

/**
 * Single exception type for every business-rule failure in this service,
 * matching the ApiException/GlobalExceptionHandler pattern used in
 * customer-service and order-service. The HTTP status travels with the
 * exception instead of being re-derived per @ExceptionHandler method.
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
