package com.poc.composite.client;

import com.poc.composite.dto.CustomerDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * "customer-service" here is the spring.application.name registered on
 * Eureka - Feign + the load balancer resolve that name to an actual
 * host:port at call time, so nothing is hardcoded.
 */
@FeignClient(name = "customer-service", fallback = CustomerClientFallback.class)
public interface CustomerClient {

    @GetMapping("/api/customers/{id}")
    CustomerDto getCustomerById(@PathVariable("id") Long id);
}
