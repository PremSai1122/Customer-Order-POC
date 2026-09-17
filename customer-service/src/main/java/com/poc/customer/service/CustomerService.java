package com.poc.customer.service;

import com.poc.customer.entity.Customer;
import com.poc.customer.exception.ApiException;
import com.poc.customer.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository repository;

    @Autowired
    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    // "if no filter is there... all customers should get fetched, or
    // otherwise you can do filtering also" - by name, by creation date, or
    // both. The date filter and the name-ascending sort are done with
    // Java 8 streams rather than another repository query per combination.
    // Active-only by default - includeInactive=true opts into seeing
    // soft-deleted (inactive) customers too.
    @Cacheable(value = "customers", key = "(#name != null ? #name : 'ALL') + '_' + (#date != null ? #date.toString() : 'ANY') + '_' + #includeInactive")
    public List<Customer> getCustomers(String name, LocalDate date, boolean includeInactive) {
        log.info("Querying customers: name={}, date={}, includeInactive={}", name, date, includeInactive);
        List<Customer> base = (name != null && !name.isBlank())
                ? repository.findByNameContainingIgnoreCase(name)
                : repository.findAll();

        List<Customer> result = base.stream()
                .filter(c -> includeInactive || c.isActive())
                .filter(c -> date == null || (c.getCreatedDate() != null && c.getCreatedDate().toLocalDate().equals(date)))
                .sorted(Comparator.comparing(Customer::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        log.debug("Query matched {} customer(s)", result.size());
        return result;
    }

    public Customer getCustomerById(Long id) {
        log.info("Looking up customer {}", id);
        // Not found is thrown as ApiException and logged once, by
        // GlobalExceptionHandler - no log.warn here to avoid a duplicate.
        Customer customer = repository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Customer not found with id: " + id));
        log.debug("Found customer {}: active={}", customer.getId(), customer.isActive());
        return customer;
    }

    @CacheEvict(value = "customers", allEntries = true)
    public Customer addCustomer(Customer customer) {
        Customer saved = repository.save(customer);
        log.debug("Persisted customer {}", saved.getId());
        return saved;
    }

    // Soft delete: flips active/inactive rather than removing the row,
    // matching "delete based on active/inactive state" from the transcript
    @CacheEvict(value = "customers", allEntries = true)
    public Customer setCustomerActiveStatus(Long id, boolean active) {
        Customer customer = getCustomerById(id);
        customer.setActive(active);
        Customer saved = repository.save(customer);
        log.debug("Customer {} active status now {}", saved.getId(), saved.isActive());
        return saved;
    }
}
