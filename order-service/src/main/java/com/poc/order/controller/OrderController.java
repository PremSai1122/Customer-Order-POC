package com.poc.order.controller;

import com.poc.order.entity.Order;
import com.poc.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    @Autowired
    public OrderController(OrderService service) {
        this.service = service;
    }

    // GET /api/orders?customerId=1                -> all orders for a customer
    // GET /api/orders?customerId=1&productId=P100 -> narrowed to one product
    @GetMapping
    public ResponseEntity<List<Order>> getOrders(
            @RequestParam Long customerId,
            @RequestParam(required = false) String productId) {
        if (productId != null && !productId.isBlank()) {
            return ResponseEntity.ok(service.getOrdersByCustomerAndProduct(customerId, productId));
        }
        return ResponseEntity.ok(service.getOrdersByCustomer(customerId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getOrderById(id));
    }

    @PostMapping
    public ResponseEntity<Order> addOrder(@Valid @RequestBody Order order) {
        Order saved = service.addOrder(order);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        service.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
