package com.salessphere.backend.service;

import com.salessphere.backend.dto.AggregationResultDto;
import com.salessphere.backend.dto.AnalyticsDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final DashboardService dashboardService;

    public AnalyticsDto getAnalyticsData() {
        log.info("Calculating rankings and revenue distribution statistics");
        
        AggregationResultDto agg = dashboardService.getDashboardData();
        long totalRevenueCents = agg.getTotalRevenue();
        BigDecimal totalRevenue = centsToDollars(totalRevenueCents);

        // Calculate Regional Rankings
        List<AnalyticsDto.RegionRanking> regionRankings = new ArrayList<>();
        List<Map.Entry<String, Long>> sortedRegions = new ArrayList<>(agg.getRegionalTotals().entrySet());
        // Sort descending using collections framework
        sortedRegions.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        int rank = 1;
        for (Map.Entry<String, Long> entry : sortedRegions) {
            long salesCents = entry.getValue();
            double share = totalRevenueCents > 0 ? (double) salesCents * 100.0 / totalRevenueCents : 0.0;

            regionRankings.add(AnalyticsDto.RegionRanking.builder()
                    .rank(rank++)
                    .regionName(entry.getKey())
                    .totalSalesCents(salesCents)
                    .totalSales(centsToDollars(salesCents))
                    .percentageShare(roundDouble(share))
                    .build());
        }

        // Calculate Category Rankings
        List<AnalyticsDto.CategoryRanking> categoryRankings = new ArrayList<>();
        List<Map.Entry<String, Long>> sortedCategories = new ArrayList<>(agg.getCategoryTotals().entrySet());
        // Sort descending
        sortedCategories.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

        rank = 1;
        for (Map.Entry<String, Long> entry : sortedCategories) {
            long salesCents = entry.getValue();
            double share = totalRevenueCents > 0 ? (double) salesCents * 100.0 / totalRevenueCents : 0.0;

            categoryRankings.add(AnalyticsDto.CategoryRanking.builder()
                    .rank(rank++)
                    .categoryName(entry.getKey())
                    .totalSalesCents(salesCents)
                    .totalSales(centsToDollars(salesCents))
                    .percentageShare(roundDouble(share))
                    .build());
        }

        return AnalyticsDto.builder()
                .regionalRankings(regionRankings)
                .categoryRankings(categoryRankings)
                .totalRevenueCents(totalRevenueCents)
                .totalRevenue(totalRevenue)
                .totalTransactions(agg.getTotalTransactions())
                .build();
    }

    private BigDecimal centsToDollars(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private double roundDouble(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
