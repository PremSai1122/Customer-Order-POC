package com.poc.composite.controller;

import com.poc.composite.client.CustomerClient;
import com.poc.composite.client.OrderClient;
import com.poc.composite.dto.CustomerDto;
import com.poc.composite.dto.CustomerOrdersResponse;
import com.poc.composite.dto.OrderDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/composite")
public class CompositeController {

    private static final Logger log = LoggerFactory.getLogger(CompositeController.class);

    private final CustomerClient customerClient;
    private final OrderClient orderClient;

    public CompositeController(CustomerClient customerClient, OrderClient orderClient) {
        this.customerClient = customerClient;
        this.orderClient = orderClient;
    }

    /**
     * "fresh customer can add the orders... call the 2 API 2 atomic
     * services inside the controller... place the order basically from
     * the controller" - this is that flow.
     *
     * 1) verify the customer exists (call customer-service)
     * 2) if they do, create the order (call order-service)
     */
    @PostMapping("/orders")
    public ResponseEntity<?> placeOrder(@RequestBody OrderDto orderRequest) {
        log.info("Place order requested: customerId={}, productId={}, quantity={}",
                orderRequest.getCustomerId(), orderRequest.getProductId(), orderRequest.getQuantity());

        CustomerDto customer = customerClient.getCustomerById(orderRequest.getCustomerId());

        if (customer == null) {
            log.warn("Rejecting order - customer {} not found or customer-service unavailable",
                    orderRequest.getCustomerId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Customer " + orderRequest.getCustomerId() + " not found or customer-service unavailable");
        }
        log.debug("Customer {} found, active={}", customer.getId(), customer.isActive());
        if (!customer.isActive()) {
            log.warn("Rejecting order - customer {} is inactive", orderRequest.getCustomerId());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Customer " + orderRequest.getCustomerId() + " is inactive");
        }

        OrderDto placedOrder = orderClient.addOrder(orderRequest);
        if (placedOrder == null) {
            log.error("order-service unavailable - order not placed for customerId={}", orderRequest.getCustomerId());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("order-service unavailable, order not placed");
        }

        log.info("Order {} placed successfully for customerId={}", placedOrder.getId(), orderRequest.getCustomerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(placedOrder);
    }

    /**
     * Combined view - one call to the composite service instead of the
     * client having to call two services and stitch the response itself.
     * This is the core reason a composite/aggregator service exists.
     */
    @GetMapping("/customers/{id}/orders")
    public ResponseEntity<CustomerOrdersResponse> getCustomerWithOrders(@PathVariable Long id) {
        log.info("Fetching combined customer+orders view for customerId={}", id);

        CustomerDto customer = customerClient.getCustomerById(id);
        if (customer == null) {
            log.warn("Customer {} not found or customer-service unavailable", id);
            return ResponseEntity.notFound().build();
        }

        var orders = orderClient.getOrdersByCustomer(id);
        log.debug("Found {} order(s) for customerId={}", orders.size(), id);

        return ResponseEntity.ok(new CustomerOrdersResponse(customer, orders));
    }
}
