package com.poc.customer.service;

import com.poc.customer.entity.Customer;
import com.poc.customer.exception.CustomerNotFoundException;
import com.poc.customer.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository repository;

    @Autowired
    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    // "if no filter is there... all customers should get fetched, or
    // otherwise you can do filtering also"
    @Cacheable(value = "customers", key = "#name != null ? #name : 'ALL'")
    public List<Customer> getCustomers(String name) {
        if (name != null && !name.isBlank()) {
            return repository.findByNameContainingIgnoreCase(name);
        }
        return repository.findAll();
    }

    public Customer getCustomerById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    @CacheEvict(value = "customers", allEntries = true)
    public Customer addCustomer(Customer customer) {
        return repository.save(customer);
    }

    // Soft delete: flips active/inactive rather than removing the row,
    // matching "delete based on active/inactive state" from the transcript
    @CacheEvict(value = "customers", allEntries = true)
    public Customer setCustomerActiveStatus(Long id, boolean active) {
        Customer customer = getCustomerById(id);
        customer.setActive(active);
        return repository.save(customer);
    }
}
