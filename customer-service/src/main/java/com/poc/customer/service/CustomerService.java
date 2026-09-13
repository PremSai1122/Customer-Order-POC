package com.poc.customer.service;

import com.poc.customer.entity.Customer;
import com.poc.customer.exception.CustomerNotFoundException;
import com.poc.customer.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    private final CustomerRepository repository;

    @Autowired
    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    // "if no filter is there... all customers should get fetched, or
    // otherwise you can do filtering also" - by name, by creation date, or
    // both. The date filter and the name-ascending sort are done with
    // Java 8 streams rather than another repository query per combination.
    @Cacheable(value = "customers", key = "(#name != null ? #name : 'ALL') + '_' + (#date != null ? #date.toString() : 'ANY')")
    public List<Customer> getCustomers(String name, LocalDate date) {
        List<Customer> base = (name != null && !name.isBlank())
                ? repository.findByNameContainingIgnoreCase(name)
                : repository.findAll();

        return base.stream()
                .filter(c -> date == null || (c.getCreatedDate() != null && c.getCreatedDate().toLocalDate().equals(date)))
                .sorted(Comparator.comparing(Customer::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
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
