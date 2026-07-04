package com.salessphere.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRequestDto {

    @NotBlank(message = "Transaction code is required")
    @Size(max = 50, message = "Transaction code must be less than 50 characters")
    private String transactionCode;

    @NotBlank(message = "Transaction date is required")
    private String transactionDate;

    private String regionName; // Derived on server, kept optional for compatibility

    @NotBlank(message = "State name is required")
    @Size(max = 100, message = "State name must be less than 100 characters")
    private String state;

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must be less than 100 characters")
    private String categoryName;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than or equal to 0.01")
    @Digits(integer = 12, fraction = 2, message = "Amount format is invalid")
    private BigDecimal amount; // Decimals are accepted from client, we will convert to cents on server

    private String product;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String paymentMethod;
    private String status;
    private String customerId;
    private String employeeId;
    private String storeId;
    private String remarks;
}
