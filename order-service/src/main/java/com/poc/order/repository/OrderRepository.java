package com.poc.order.repository;

import com.poc.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // "you should fetch the order by customer" / "by customer and the product ID"
    List<Order> findByCustomerId(Long customerId);

    List<Order> findByCustomerIdAndProductId(Long customerId, String productId);
}
