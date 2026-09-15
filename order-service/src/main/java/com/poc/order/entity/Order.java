package com.poc.order.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;

@Entity
// Every read path in this service filters on customer_id - findByCustomerId is
// what composite-service calls on each combined customer+orders view. Without
// these indexes Postgres full-scans the orders table for every one of those
// calls. ddl-auto=update creates them on next startup; on a pre-existing
// database they can also be added by hand with CREATE INDEX.
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_customer_id", columnList = "customer_id"),
        @Index(name = "idx_orders_customer_product", columnList = "customer_id, product_id")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // NOTE: this is intentionally just a Long, not a @ManyToOne to a Customer
    // entity. Customer lives in a different service/database - microservices
    // don't do cross-service JPA joins. The composite service is what stitches
    // customer + order data together at the API layer.
    @NotNull
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    // @NotBlank, not @NotNull: the column is non-nullable, but "" and "   " would
    // both pass a null check and then persist as a meaningless product id.
    @NotBlank
    @Column(name = "product_id", nullable = false)
    private String productId;

    // @Positive on its own passes when quantity is null, which then fails at the
    // database as a 500 rather than being reported as a 400 with a clear message.
    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private String status = "PLACED";

    @Column(name = "order_date", updatable = false)
    private LocalDateTime orderDate;

    @PrePersist
    protected void onCreate() {
        this.orderDate = LocalDateTime.now();
    }

    public Order() {
    }

    public Order(Long customerId, String productId, Integer quantity) {
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getOrderDate() { return orderDate; }
    public void setOrderDate(LocalDateTime orderDate) { this.orderDate = orderDate; }
}
