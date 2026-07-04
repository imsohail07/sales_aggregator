package com.salessphere.backend.dto;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AggregationResultDto {
    private Map<String, Map<String, Long>> regionCategorySales;
    private Map<String, Long> regionalTotals;
    private Map<String, Long> categoryTotals;
    private Map<String, Long> monthlyTotals;
    private Map<String, Long> monthlyTransactionCounts;
    private Map<String, Long> paymentMethodTotals;
    private Map<String, Long> statusTotals;
    private Map<String, String> topCategoriesPerRegion;
    private String overallBestRegion;

    // Drill-down support
    private Map<String, java.util.Set<String>> regionStates;
    private Map<String, Long> stateTotals;
    private Map<String, Map<String, Long>> stateCategorySales;

    // Overall KPIs
    private long totalTransactions;
    private long totalRevenue; // in cents
    private int totalRegions;
    private int totalCategories;
    private double averageTransactionValue; // in cents
    private long highestTransactionValue; // in cents
    private long lowestTransactionValue; // in cents
    private String topRegionName;
    private String topCategoryName;
    private String latestImportTime; // formatted timestamp
}
