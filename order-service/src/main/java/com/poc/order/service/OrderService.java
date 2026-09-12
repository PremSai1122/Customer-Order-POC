package com.poc.order.service;

import com.poc.order.entity.Order;
import com.poc.order.exception.OrderNotFoundException;
import com.poc.order.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderService {

    private final OrderRepository repository;

    @Autowired
    public OrderService(OrderRepository repository) {
        this.repository = repository;
    }

    public List<Order> getOrdersByCustomer(Long customerId) {
        return repository.findByCustomerId(customerId);
    }

    public List<Order> getOrdersByCustomerAndProduct(Long customerId, String productId) {
        return repository.findByCustomerIdAndProductId(customerId, productId);
    }

    public Order getOrderById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public Order addOrder(Order order) {
        return repository.save(order);
    }

    public void deleteOrder(Long id) {
        Order order = getOrderById(id);
        repository.delete(order);
    }
}
