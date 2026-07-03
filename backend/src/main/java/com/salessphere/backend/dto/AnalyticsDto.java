package com.salessphere.backend.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsDto {
    private List<RegionRanking> regionalRankings;
    private List<CategoryRanking> categoryRankings;
    private long totalRevenueCents;
    private BigDecimal totalRevenue;
    private long totalTransactions;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RegionRanking {
        private int rank;
        private String regionName;
        private long totalSalesCents;
        private BigDecimal totalSales;
        private double percentageShare;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CategoryRanking {
        private int rank;
        private String categoryName;
        private long totalSalesCents;
        private BigDecimal totalSales;
        private double percentageShare;
    }
}
