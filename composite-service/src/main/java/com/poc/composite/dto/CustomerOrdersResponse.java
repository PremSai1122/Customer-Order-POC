package com.poc.composite.dto;

import java.util.List;

public class CustomerOrdersResponse {

    private CustomerDto customer;
    private List<OrderDto> orders;

    public CustomerOrdersResponse(CustomerDto customer, List<OrderDto> orders) {
        this.customer = customer;
        this.orders = orders;
    }

    public CustomerDto getCustomer() { return customer; }
    public void setCustomer(CustomerDto customer) { this.customer = customer; }

    public List<OrderDto> getOrders() { return orders; }
    public void setOrders(List<OrderDto> orders) { this.orders = orders; }
}
