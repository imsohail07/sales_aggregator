package com.salessphere.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions", indexes = {
    @Index(name = "idx_transaction_code", columnList = "transaction_code"),
    @Index(name = "idx_transaction_date", columnList = "transaction_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_code", unique = true, nullable = false, length = 50)
    private String transactionCode;

    @Column(name = "transaction_date", nullable = false)
    private LocalDateTime transactionDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "amount_cents", nullable = false)
    private Long amountCents;

    @Column(name = "product", length = 255)
    private String product;

    @Column(name = "quantity")
    private Integer quantity;

    @Column(name = "unit_price_cents")
    private Long unitPriceCents;

    @Column(name = "payment_method", length = 100)
    private String paymentMethod;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "customer_id", length = 100)
    private String customerId;

    @Column(name = "employee_id", length = 100)
    private String employeeId;

    @Column(name = "store_id", length = 100)
    private String storeId;

    @Column(name = "remarks", length = 1000)
    private String remarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
