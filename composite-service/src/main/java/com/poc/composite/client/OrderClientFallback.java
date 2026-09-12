package com.poc.composite.client;

import com.poc.composite.dto.OrderDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class OrderClientFallback implements OrderClient {

    private static final Logger log = LoggerFactory.getLogger(OrderClientFallback.class);

    @Override
    public List<OrderDto> getOrdersByCustomer(Long customerId) {
        log.warn("Fallback triggered: order-service unavailable for customerId={}", customerId);
        return Collections.emptyList();
    }

    @Override
    public OrderDto addOrder(OrderDto order) {
        log.warn("Fallback triggered: order-service unavailable, could not place order for customerId={}",
                order.getCustomerId());
        return null;
    }
}
