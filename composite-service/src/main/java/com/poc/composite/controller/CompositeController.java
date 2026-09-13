package com.poc.composite.controller;

import com.poc.composite.client.CustomerClient;
import com.poc.composite.client.OrderClient;
import com.poc.composite.dto.CustomerDto;
import com.poc.composite.dto.CustomerOrdersResponse;
import com.poc.composite.dto.OrderDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/composite")
public class CompositeController {

    private final CustomerClient customerClient;
    private final OrderClient orderClient;

    @Autowired
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
        CustomerDto customer = customerClient.getCustomerById(orderRequest.getCustomerId());

        if (customer == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Customer " + orderRequest.getCustomerId() + " not found or customer-service unavailable");
        }
        if (!customer.isActive()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Customer " + orderRequest.getCustomerId() + " is inactive");
        }

        OrderDto placedOrder = orderClient.addOrder(orderRequest);
        if (placedOrder == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("order-service unavailable, order not placed");
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(placedOrder);
    }

    /**
     * Combined view - one call to the composite service instead of the
     * client having to call two services and stitch the response itself.
     * This is the core reason a composite/aggregator service exists.
     */
    @GetMapping("/customers/{id}/orders")
    public ResponseEntity<CustomerOrdersResponse> getCustomerWithOrders(@PathVariable Long id) {
        CustomerDto customer = customerClient.getCustomerById(id);
        if (customer == null) {
            return ResponseEntity.notFound().build();
        }
        var orders = orderClient.getOrdersByCustomer(id);
        return ResponseEntity.ok(new CustomerOrdersResponse(customer, orders));
    }
}
