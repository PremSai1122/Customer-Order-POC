package com.poc.order.service;

import com.poc.order.entity.Order;
import com.poc.order.exception.OrderNotFoundException;
import com.poc.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
        return repository.findByCustomerId(customerId).stream()
                .sorted(Comparator.comparing(Order::getOrderDate).reversed())
                .collect(Collectors.toList());
    }

    public List<Order> getOrdersByCustomerAndProduct(Long customerId, Long productId) {
        return repository.findByCustomerIdAndProductId(customerId, productId).stream()
                .sorted(Comparator.comparing(Order::getOrderDate).reversed())
                .collect(Collectors.toList());
    }

    // No customerId filter - returns every order. Cached like the per-customer
    // lookups so an unfiltered listing doesn't hit the database on every call.
    @Cacheable(value = "ordersByCustomer", key = "'ALL'")
    public List<Order> getAllOrders() {
        return repository.findAll().stream()
                .sorted(Comparator.comparing(Order::getOrderDate).reversed())
                .collect(Collectors.toList());
    }

    public Order getOrderById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
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
