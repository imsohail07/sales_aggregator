package com.salessphere.backend.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class TransactionRequestDto {

    @NotBlank(message = "Transaction code is required")
    @Size(max = 50, message = "Transaction code must be less than 50 characters")
    private String transactionCode;

    @NotNull(message = "Transaction date is required")
    private LocalDate transactionDate;

    @NotBlank(message = "Region name is required")
    @Size(max = 100, message = "Region name must be less than 100 characters")
    private String regionName;

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must be less than 100 characters")
    private String categoryName;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than or equal to 0.01")
    @Digits(integer = 12, fraction = 2, message = "Amount format is invalid")
    private BigDecimal amount; // Decimals are accepted from client, we will convert to cents on server
}
