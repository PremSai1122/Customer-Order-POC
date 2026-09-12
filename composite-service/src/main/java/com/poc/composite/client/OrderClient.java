package com.poc.composite.client;

import com.poc.composite.dto.OrderDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(name = "order-service", fallback = OrderClientFallback.class)
public interface OrderClient {

    @GetMapping("/api/orders")
    List<OrderDto> getOrdersByCustomer(@RequestParam("customerId") Long customerId);

    @PostMapping("/api/orders")
    OrderDto addOrder(@RequestBody OrderDto order);
}
