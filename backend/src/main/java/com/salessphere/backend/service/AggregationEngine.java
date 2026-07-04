package com.salessphere.backend.service;

import com.salessphere.backend.dto.AggregationResultDto;
import com.salessphere.backend.entity.Transaction;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Stream;

@Service
public class AggregationEngine {

    /**
     * Aggregates raw transactions into region/category summaries and computes BI KPIs.
     * Uses core Java Collections and streaming inputs for O(N) performance.
     *
     * @param transactionStream Stream of transactions to aggregate.
     * @return AggregationResultDto with aggregated summaries and statistics.
     */
    public AggregationResultDto aggregate(Stream<Transaction> transactionStream) {
        // Initializing Collections for Aggregation
        Map<String, Map<String, Long>> regionCategorySales = new HashMap<>();
        Map<String, Long> regionalTotals = new HashMap<>();
        Map<String, Long> categoryTotals = new HashMap<>();
        Map<String, Set<String>> regionStates = new HashMap<>();
        Map<String, Long> stateTotals = new HashMap<>();
        Map<String, Map<String, Long>> stateCategorySales = new HashMap<>();
        Map<String, Long> monthlyTotals = new TreeMap<>();
        Map<String, Long> monthlyTransactionCounts = new TreeMap<>();
        Map<String, Long> paymentMethodTotals = new HashMap<>();
        Map<String, Long> statusTotals = new HashMap<>();

        long totalTransactions = 0;
        long highestTransactionValue = Long.MIN_VALUE;
        long lowestTransactionValue = Long.MAX_VALUE;

        // Loop over transactions
        Iterator<Transaction> iterator = transactionStream.iterator();
        while (iterator.hasNext()) {
            Transaction tx = iterator.next();
            String regionName = tx.getRegion().getName();
            String stateName = tx.getState() != null ? tx.getState() : "Unknown";
            String categoryName = tx.getCategory().getName();
            long amount = tx.getAmountCents();

            // Aggregation Use Case 1: Region -> Category -> Total Sales
            // Required: computeIfAbsent(), merge()
            regionCategorySales
                .computeIfAbsent(regionName, k -> new HashMap<>())
                .merge(categoryName, amount, Long::sum);

            // Aggregation Use Case 2: Regional Totals & Category Totals
            // Required: merge()
            regionalTotals.merge(regionName, amount, Long::sum);
            categoryTotals.merge(categoryName, amount, Long::sum);

            // Drill-down collections mapping:
            // 1. Region -> States list
            regionStates
                .computeIfAbsent(regionName, k -> new HashSet<>())
                .add(stateName);

            // 2. State -> Total Sales
            stateTotals.merge(stateName, amount, Long::sum);

            // 3. State -> Category -> Total Sales
            stateCategorySales
                .computeIfAbsent(stateName, k -> new HashMap<>())
                .merge(categoryName, amount, Long::sum);

            // 4. Monthly timelines
            java.time.LocalDateTime dateTime = tx.getTransactionDate();
            String monthKey = "Unknown";
            if (dateTime != null) {
                int year = dateTime.getYear();
                int month = dateTime.getMonthValue();
                monthKey = String.format("%04d-%02d", year, month);
            }
            monthlyTotals.merge(monthKey, amount, Long::sum);
            monthlyTransactionCounts.merge(monthKey, 1L, Long::sum);

            // 5. Payment Methods
            String paymentMethod = tx.getPaymentMethod() != null && !tx.getPaymentMethod().isEmpty() 
                    ? tx.getPaymentMethod() : "Unknown";
            paymentMethodTotals.merge(paymentMethod, amount, Long::sum);

            // 6. Status Codes
            String status = tx.getStatus() != null && !tx.getStatus().isEmpty() 
                    ? tx.getStatus() : "Unknown";
            statusTotals.merge(status, amount, Long::sum);

            // Overall KPIs
            totalTransactions++;
            if (amount > highestTransactionValue) {
                highestTransactionValue = amount;
            }
            if (amount < lowestTransactionValue) {
                lowestTransactionValue = amount;
            }
        }

        // Adjust edge case when no transactions exist
        if (totalTransactions == 0) {
            lowestTransactionValue = 0;
            highestTransactionValue = 0;
        }

        // Validate consistency and satisfy Required: values() method
        // Summing values of regionalTotals to calculate total revenue
        long totalRevenue = 0;
        Collection<Long> totals = regionalTotals.values();
        for (Long val : totals) {
            totalRevenue += val;
        }

        // Aggregation Use Case 3: Find Top Category for every Region
        // Required: entrySet()
        Map<String, String> topCategoriesPerRegion = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Long>> regionEntry : regionCategorySales.entrySet()) {
            String region = regionEntry.getKey();
            Map<String, Long> categorySales = regionEntry.getValue();

            String topCategory = null;
            long maxSales = -1;
            for (Map.Entry<String, Long> catEntry : categorySales.entrySet()) {
                if (catEntry.getValue() > maxSales) {
                    maxSales = catEntry.getValue();
                    topCategory = catEntry.getKey();
                }
            }
            if (topCategory != null) {
                topCategoriesPerRegion.put(region, topCategory);
            }
        }

        // Aggregation Use Case 4: Find Overall Best Performing Region
        // Required: entrySet()
        String overallBestRegion = null;
        long maxRegionSales = -1;
        for (Map.Entry<String, Long> regionTotalEntry : regionalTotals.entrySet()) {
            if (regionTotalEntry.getValue() > maxRegionSales) {
                maxRegionSales = regionTotalEntry.getValue();
                overallBestRegion = regionTotalEntry.getKey();
            }
        }

        // Find Overall Best Category
        String topCategoryOverall = null;
        long maxCategorySales = -1;
        for (Map.Entry<String, Long> categoryTotalEntry : categoryTotals.entrySet()) {
            if (categoryTotalEntry.getValue() > maxCategorySales) {
                maxCategorySales = categoryTotalEntry.getValue();
                topCategoryOverall = categoryTotalEntry.getKey();
            }
        }

        // Required: keySet() to query dimensions
        int totalRegions = regionalTotals.keySet().size();
        int totalCategories = categoryTotals.keySet().size();

        double averageTransactionValue = totalTransactions > 0 
                ? (double) totalRevenue / totalTransactions 
                : 0.0;

        return AggregationResultDto.builder()
                .regionCategorySales(regionCategorySales)
                .regionalTotals(regionalTotals)
                .categoryTotals(categoryTotals)
                .monthlyTotals(monthlyTotals)
                .monthlyTransactionCounts(monthlyTransactionCounts)
                .paymentMethodTotals(paymentMethodTotals)
                .statusTotals(statusTotals)
                .topCategoriesPerRegion(topCategoriesPerRegion)
                .overallBestRegion(overallBestRegion)
                .regionStates(regionStates)
                .stateTotals(stateTotals)
                .stateCategorySales(stateCategorySales)
                .totalTransactions(totalTransactions)
                .totalRevenue(totalRevenue)
                .totalRegions(totalRegions)
                .totalCategories(totalCategories)
                .averageTransactionValue(averageTransactionValue)
                .highestTransactionValue(highestTransactionValue)
                .lowestTransactionValue(lowestTransactionValue)
                .topRegionName(overallBestRegion)
                .topCategoryName(topCategoryOverall)
                .build();
    }
}
