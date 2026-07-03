package com.salessphere.backend.service;

import com.salessphere.backend.dto.AggregationResultDto;
import com.salessphere.backend.entity.Transaction;
import com.salessphere.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final AggregationEngine aggregationEngine;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Transactional(readOnly = true)
    public AggregationResultDto getDashboardData() {
        log.info("Generating dashboard statistics via stream aggregation");
        
        AggregationResultDto result;
        // Using try-with-resources to ensure the database stream is closed properly
        try (Stream<Transaction> stream = transactionRepository.streamAllTransactions()) {
            result = aggregationEngine.aggregate(stream);
        }

        // Add latest transaction timestamp if present
        transactionRepository.findFirstByOrderByCreatedAtDesc().ifPresent(latestTx -> {
            result.setLatestImportTime(DATE_TIME_FORMATTER.format(latestTx.getCreatedAt()));
        });

        return result;
    }
}
