package com.poc.customer.service;

import com.poc.customer.entity.Customer;
import com.poc.customer.exception.CustomerNotFoundException;
import com.poc.customer.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private static final Comparator<Customer> BY_NAME =
            Comparator.comparing(Customer::getName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    // "if no filter is there... all customers should get fetched, or
    // otherwise you can do filtering also" - by name, by creation date, or
    // both. The date filter and the name-ascending sort are done with
    // Java 8 streams rather than another repository query per combination.
    //
    // The cache key separates its two parts with a character that cannot appear
    // in an ISO date, so ("a", 2026-09-12) and ("a|2026-09-12", null) cannot
    // collide on the same entry the way simple concatenation allowed.
    @Transactional(readOnly = true)
    @Cacheable(value = "customers",
            key = "(#name != null ? #name : 'ALL') + '|' + (#date != null ? #date.toString() : 'ANY')")
    public List<Customer> getCustomers(String name, LocalDate date) {
        List<Customer> base = (name != null && !name.isBlank())
                ? repository.findByNameContainingIgnoreCase(name)
                : repository.findAll();

        return base.stream()
                .filter(c -> date == null || (c.getCreatedDate() != null && c.getCreatedDate().toLocalDate().equals(date)))
                .sorted(BY_NAME)
                .toList();
    }

    @Transactional(readOnly = true)
    public Customer getCustomerById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    @Transactional
    @CacheEvict(value = "customers", allEntries = true)
    public Customer addCustomer(Customer customer) {
        Customer saved = repository.save(customer);
        log.debug("Persisted customer {}", saved.getId());
        return saved;
    }

    // Soft delete: flips active/inactive rather than removing the row,
    // matching "delete based on active/inactive state" from the transcript
    @Transactional
    @CacheEvict(value = "customers", allEntries = true)
    public Customer setCustomerActiveStatus(Long id, boolean active) {
        Customer customer = getCustomerById(id);
        customer.setActive(active);
        Customer saved = repository.save(customer);
        log.debug("Customer {} active status now {}", saved.getId(), saved.isActive());
        return saved;
    }
}
