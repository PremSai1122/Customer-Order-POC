package com.poc.order.controller;

import com.poc.order.entity.Order;
import com.poc.order.service.OrderService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService service;

    @Autowired
    public OrderController(OrderService service) {
        this.service = service;
    }

    // GET /api/orders                            -> all orders
    // GET /api/orders?customerId=1                -> all orders for a customer
    // GET /api/orders?customerId=1&productId=100  -> narrowed to one product
    @GetMapping
    public ResponseEntity<List<Order>> getOrders(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long productId) {
        log.info("Fetching orders: customerId={}, productId={}", customerId, productId);
        List<Order> orders;
        if (customerId == null) {
            orders = service.getAllOrders();
        } else if (productId != null) {
            orders = service.getOrdersByCustomerAndProduct(customerId, productId);
        } else {
            orders = service.getOrdersByCustomer(customerId);
        }
        log.debug("Found {} order(s) for customerId={}", orders.size(), customerId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable Long id) {
        log.info("Fetching order {}", id);
        return ResponseEntity.ok(service.getOrderById(id));
    }

    @PostMapping
    public ResponseEntity<Order> addOrder(@Valid @RequestBody Order order) {
        log.info("Adding order: customerId={}, productId={}, quantity={}",
                order.getCustomerId(), order.getProductId(), order.getQuantity());
        Order saved = service.addOrder(order);
        log.info("Order {} created", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id) {
        log.info("Deleting order {}", id);
        service.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
