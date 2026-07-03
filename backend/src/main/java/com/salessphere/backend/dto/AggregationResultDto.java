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
    private Map<String, String> topCategoriesPerRegion;
    private String overallBestRegion;

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
