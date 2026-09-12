package com.poc.composite.client;

import com.poc.composite.dto.CustomerDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs when customer-service is down, slow, or the circuit breaker has
 * tripped open. Returning null here (and having the controller check for
 * it) is the simplest option for a POC - in a real system you'd usually
 * throw a specific exception the @ControllerAdvice can turn into a 503.
 */
@Component
public class CustomerClientFallback implements CustomerClient {

    private static final Logger log = LoggerFactory.getLogger(CustomerClientFallback.class);

    @Override
    public CustomerDto getCustomerById(Long id) {
        log.warn("Fallback triggered: customer-service unavailable for customerId={}", id);
        return null;
    }
}
