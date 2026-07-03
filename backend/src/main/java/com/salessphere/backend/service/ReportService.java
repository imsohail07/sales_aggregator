package com.salessphere.backend.service;

import com.salessphere.backend.dto.AggregationResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final DashboardService dashboardService;

    public String generateRegionalReportCsv() {
        log.info("Generating regional report CSV");
        AggregationResultDto agg = dashboardService.getDashboardData();
        
        StringBuilder csv = new StringBuilder();
        csv.append("Region,Category,Sales (USD)\n");

        for (Map.Entry<String, Map<String, Long>> regionEntry : agg.getRegionCategorySales().entrySet()) {
            String region = regionEntry.getKey();
            for (Map.Entry<String, Long> catEntry : regionEntry.getValue().entrySet()) {
                String category = catEntry.getKey();
                BigDecimal sales = centsToDollars(catEntry.getValue());
                csv.append(String.format("\"%s\",\"%s\",%s\n", region, category, sales.toString()));
            }
        }
        return csv.toString();
    }

    public String generateCategoryReportCsv() {
        log.info("Generating category report CSV");
        AggregationResultDto agg = dashboardService.getDashboardData();

        StringBuilder csv = new StringBuilder();
        csv.append("Category,Total Sales (USD)\n");

        for (Map.Entry<String, Long> entry : agg.getCategoryTotals().entrySet()) {
            BigDecimal sales = centsToDollars(entry.getValue());
            csv.append(String.format("\"%s\",%s\n", entry.getKey(), sales.toString()));
        }
        return csv.toString();
    }

    public String generateExecutiveSummaryCsv() {
        log.info("Generating executive summary CSV");
        AggregationResultDto agg = dashboardService.getDashboardData();

        StringBuilder csv = new StringBuilder();
        csv.append("Metric,Value\n");
        csv.append(String.format("\"Total Transactions\",%d\n", agg.getTotalTransactions()));
        csv.append(String.format("\"Total Revenue (USD)\",%s\n", centsToDollars(agg.getTotalRevenue()).toString()));
        csv.append(String.format("\"Total Regions\",%d\n", agg.getTotalRegions()));
        csv.append(String.format("\"Total Categories\",%d\n", agg.getTotalCategories()));
        csv.append(String.format("\"Average Transaction Value (USD)\",%s\n", centsToDollars((long) agg.getAverageTransactionValue()).toString()));
        csv.append(String.format("\"Highest Transaction (USD)\",%s\n", centsToDollars(agg.getHighestTransactionValue()).toString()));
        csv.append(String.format("\"Lowest Transaction (USD)\",%s\n", centsToDollars(agg.getLowestTransactionValue()).toString()));
        csv.append(String.format("\"Top Performing Region\",\"%s\"\n", agg.getTopRegionName() != null ? agg.getTopRegionName() : "N/A"));
        csv.append(String.format("\"Top Product Category\",\"%s\"\n", agg.getTopCategoryName() != null ? agg.getTopCategoryName() : "N/A"));
        csv.append(String.format("\"Latest Import Time\",\"%s\"\n", agg.getLatestImportTime() != null ? agg.getLatestImportTime() : "N/A"));
        
        return csv.toString();
    }

    private BigDecimal centsToDollars(long cents) {
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
