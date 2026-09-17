package com.poc.order.service;

import com.poc.order.entity.Order;
import com.poc.order.exception.ApiException;
import com.poc.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository repository;

    @Autowired
    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    // Most recent order first - sorted with Java 8 streams rather than a
    // repository-level ORDER BY, since this is the one place in the codebase
    // demonstrating that approach per the requirements checklist.
    @Cacheable(value = "ordersByCustomer", key = "#customerId")
    public List<Order> getOrdersByCustomer(Long customerId) {
        log.info("Querying orders for customerId={}", customerId);
        List<Order> orders = repository.findByCustomerId(customerId).stream()
                .sorted(Comparator.comparing(Order::getOrderDate).reversed())
                .collect(Collectors.toList());
        log.debug("Query matched {} order(s) for customerId={}", orders.size(), customerId);
        return orders;
    }

    public List<Order> getOrdersByCustomerAndProduct(Long customerId, Long productId) {
        log.info("Querying orders for customerId={}, productId={}", customerId, productId);
        List<Order> orders = repository.findByCustomerIdAndProductId(customerId, productId).stream()
                .sorted(Comparator.comparing(Order::getOrderDate).reversed())
                .collect(Collectors.toList());
        log.debug("Query matched {} order(s) for customerId={}, productId={}", orders.size(), customerId, productId);
        return orders;
    }

    // No customerId filter - returns every order. Cached like the per-customer
    // lookups so an unfiltered listing doesn't hit the database on every call.
    @Cacheable(value = "ordersByCustomer", key = "'ALL'")
    public List<Order> getAllOrders() {
        log.info("Querying all orders");
        List<Order> orders = repository.findAll().stream()
                .sorted(Comparator.comparing(Order::getOrderDate).reversed())
                .collect(Collectors.toList());
        log.debug("Query matched {} order(s)", orders.size());
        return orders;
    }

    public Order getOrderById(Long id) {
        log.info("Looking up order {}", id);
        // Not found is thrown as ApiException and logged once, by
        // GlobalExceptionHandler - no log.warn here to avoid a duplicate.
        Order order = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found with id: " + id));
        log.debug("Found order {}: status={}", order.getId(), order.getStatus());
        return order;
    }

    // Evicting the whole cache (rather than a single customerId key) keeps
    // this correct without needing #result, which @CacheEvict doesn't expose.
    @CacheEvict(value = "ordersByCustomer", allEntries = true)
    public Order addOrder(Order order) {
        Order saved = repository.save(order);
        log.debug("Persisted order {}", saved.getId());
        return saved;
    }

    @CacheEvict(value = "ordersByCustomer", allEntries = true)
    public void deleteOrder(Long id) {
        Order order = getOrderById(id);
        repository.delete(order);
        log.debug("Deleted order {}", id);
    }
}
