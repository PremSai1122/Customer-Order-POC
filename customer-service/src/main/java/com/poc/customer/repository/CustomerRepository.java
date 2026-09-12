package com.poc.customer.repository;

import com.poc.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // "if no filter... all customers should get fetched, or otherwise
    // filtering by name" - this covers the name filter case
    List<Customer> findByNameContainingIgnoreCase(String name);

    List<Customer> findByActive(boolean active);
}
