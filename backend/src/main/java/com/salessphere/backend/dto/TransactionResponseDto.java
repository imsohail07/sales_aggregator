package com.salessphere.backend.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResponseDto {
    private Long id;
    private String transactionCode;
    private LocalDate transactionDate;
    private String regionName;
    private String categoryName;
    private BigDecimal amount; // Decimals representing standard currency units (e.g. 100.50)
    private Long amountCents;
    private String createdByUsername;
}
