package com.poc.composite.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// Null fields (e.g. status/id on a create request) must be omitted, not sent
// as explicit JSON nulls - otherwise they overwrite order-service's entity
// defaults (like status="PLACED") instead of leaving them alone.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderDto {

    private Long id;
    private Long customerId;
    private Long productId;
    private Integer quantity;
    private String status;

    public OrderDto() {
    }

    public OrderDto(Long customerId, Long productId, Integer quantity) {
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
