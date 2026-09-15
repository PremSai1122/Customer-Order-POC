package com.poc.order.service;

import com.poc.order.entity.Order;
import com.poc.order.exception.OrderNotFoundException;
import com.poc.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // Most recent order first. orderDate is written by @PrePersist so rows this
    // service creates always have one, but a row inserted straight into Postgres
    // by hand can leave it null - nullsLast keeps that from throwing a
    // NullPointerException out of the comparator and turning a read into a 500.
    private static final Comparator<Order> NEWEST_FIRST =
            Comparator.comparing(Order::getOrderDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed();

    private final OrderRepository repository;

    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    // Sorted with Java 8 streams rather than a repository-level ORDER BY, since
    // this is the one place in the codebase demonstrating that approach per the
    // requirements checklist.
    @Transactional(readOnly = true)
    @Cacheable(value = "ordersByCustomer", key = "'all_' + #customerId")
    public List<Order> getOrdersByCustomer(Long customerId) {
        return repository.findByCustomerId(customerId).stream()
                .sorted(NEWEST_FIRST)
                .toList();
    }

    // Shares the "ordersByCustomer" cache under a distinct key prefix, so the
    // allEntries eviction on write below clears this variant too. Previously
    // this path was uncached while the other was, which meant a filtered read
    // could return rows a write had already invalidated for the unfiltered one.
    @Transactional(readOnly = true)
    @Cacheable(value = "ordersByCustomer", key = "'product_' + #customerId + '_' + #productId")
    public List<Order> getOrdersByCustomerAndProduct(Long customerId, String productId) {
        return repository.findByCustomerIdAndProductId(customerId, productId).stream()
                .sorted(NEWEST_FIRST)
                .toList();
    }

    @Transactional(readOnly = true)
    public Order getOrderById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    // Evicting the whole cache (rather than a single customerId key) keeps
    // this correct without needing #result, which @CacheEvict doesn't expose.
    @Transactional
    @CacheEvict(value = "ordersByCustomer", allEntries = true)
    public Order addOrder(Order order) {
        Order saved = repository.save(order);
        log.debug("Persisted order {}", saved.getId());
        return saved;
    }

    @Transactional
    @CacheEvict(value = "ordersByCustomer", allEntries = true)
    public void deleteOrder(Long id) {
        // deleteById would silently no-op on a missing id; going through
        // getOrderById keeps the 404 that the API contract promises.
        Order order = getOrderById(id);
        repository.delete(order);
        log.debug("Deleted order {}", id);
    }
}
