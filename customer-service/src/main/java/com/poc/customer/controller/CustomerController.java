package com.poc.customer.controller;

import com.poc.customer.entity.Customer;
import com.poc.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    // GET /api/customers                    -> all customers
    // GET /api/customers?name=raj            -> filtered by name
    // GET /api/customers?date=2026-09-12     -> filtered by creation date
    // GET /api/customers?name=raj&date=...   -> both filters combined
    @GetMapping
    public ResponseEntity<List<Customer>> getCustomers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.info("Fetching customers: nameFilterApplied={}, date={}", name != null && !name.isBlank(), date);
        List<Customer> customers = service.getCustomers(name, date);
        log.debug("Found {} customer(s)", customers.size());
        return ResponseEntity.ok(customers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable Long id) {
        log.info("Fetching customer {}", id);
        return ResponseEntity.ok(service.getCustomerById(id));
    }

    @PostMapping
    public ResponseEntity<Customer> addCustomer(@Valid @RequestBody Customer customer) {
        log.info("Adding customer");
        Customer saved = service.addCustomer(customer);
        log.info("Customer {} created", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // Soft delete - PATCH /api/customers/5/status?active=false
    @PatchMapping("/{id}/status")
    public ResponseEntity<Customer> setStatus(
            @PathVariable Long id,
            @RequestParam boolean active) {
        log.info("Setting customer {} active={}", id, active);
        return ResponseEntity.ok(service.setCustomerActiveStatus(id, active));
    }
}
