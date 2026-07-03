package com.salessphere.backend.service;

import com.salessphere.backend.dto.AggregationResultDto;
import com.salessphere.backend.entity.Category;
import com.salessphere.backend.entity.Region;
import com.salessphere.backend.entity.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class AggregationEngineTest {

    private AggregationEngine aggregationEngine;
    private Region regionNorth;
    private Region regionSouth;
    private Category categoryElectronics;
    private Category categoryApparel;

    @BeforeEach
    public void setUp() {
        aggregationEngine = new AggregationEngine();
        
        regionNorth = Region.builder().id(1L).name("North").build();
        regionSouth = Region.builder().id(2L).name("South").build();
        
        categoryElectronics = Category.builder().id(1L).name("Electronics").build();
        categoryApparel = Category.builder().id(2L).name("Apparel").build();
    }

    @Test
    public void testAggregate_EmptyStream() {
        Stream<Transaction> txStream = Stream.empty();
        AggregationResultDto result = aggregationEngine.aggregate(txStream);

        assertEquals(0, result.getTotalTransactions());
        assertEquals(0, result.getTotalRevenue());
        assertEquals(0, result.getTotalRegions());
        assertEquals(0, result.getTotalCategories());
        assertEquals(0.0, result.getAverageTransactionValue());
        assertEquals(0, result.getHighestTransactionValue());
        assertEquals(0, result.getLowestTransactionValue());
        assertNull(result.getTopRegionName());
        assertNull(result.getTopCategoryName());
        assertTrue(result.getRegionCategorySales().isEmpty());
    }

    @Test
    public void testAggregate_NormalTransactions() {
        List<Transaction> transactions = new ArrayList<>();
        
        // North Electronics: $150.00
        transactions.add(Transaction.builder()
                .transactionCode("TXN001")
                .transactionDate(LocalDateTime.now())
                .region(regionNorth)
                .category(categoryElectronics)
                .amountCents(15000L)
                .build());

        // North Apparel: $50.00
        transactions.add(Transaction.builder()
                .transactionCode("TXN002")
                .transactionDate(LocalDateTime.now())
                .region(regionNorth)
                .category(categoryApparel)
                .amountCents(5000L)
                .build());

        // South Electronics: $200.00
        transactions.add(Transaction.builder()
                .transactionCode("TXN003")
                .transactionDate(LocalDateTime.now())
                .region(regionSouth)
                .category(categoryElectronics)
                .amountCents(20000L)
                .build());

        // South Apparel: $300.00
        transactions.add(Transaction.builder()
                .transactionCode("TXN004")
                .transactionDate(LocalDateTime.now())
                .region(regionSouth)
                .category(categoryApparel)
                .amountCents(30000L)
                .build());

        // North Electronics: $100.00 (additional)
        transactions.add(Transaction.builder()
                .transactionCode("TXN005")
                .transactionDate(LocalDateTime.now())
                .region(regionNorth)
                .category(categoryElectronics)
                .amountCents(10000L)
                .build());

        AggregationResultDto result = aggregationEngine.aggregate(transactions.stream());

        // Total Counts & Sums
        assertEquals(5, result.getTotalTransactions());
        assertEquals(80000L, result.getTotalRevenue()); // 150 + 50 + 200 + 300 + 100 = 800
        assertEquals(2, result.getTotalRegions());
        assertEquals(2, result.getTotalCategories());
        assertEquals(16000.0, result.getAverageTransactionValue()); // 80000 / 5 = 16000
        assertEquals(30000L, result.getHighestTransactionValue()); // TXN004
        assertEquals(5000L, result.getLowestTransactionValue()); // TXN002

        // Regional totals
        // North: 150 + 50 + 100 = 300 ($300.00)
        // South: 200 + 300 = 500 ($500.00)
        assertEquals(30000L, result.getRegionalTotals().get("North"));
        assertEquals(50000L, result.getRegionalTotals().get("South"));
        assertEquals("South", result.getOverallBestRegion());

        // Category totals
        // Electronics: 150 + 200 + 100 = 450 ($450.00)
        // Apparel: 50 + 300 = 350 ($350.00)
        assertEquals(45000L, result.getCategoryTotals().get("Electronics"));
        assertEquals(35000L, result.getCategoryTotals().get("Apparel"));
        assertEquals("Electronics", result.getTopCategoryName());

        // Top Category Per Region
        // North: Electronics (250) vs Apparel (50) -> Electronics
        // South: Apparel (300) vs Electronics (200) -> Apparel
        assertEquals("Electronics", result.getTopCategoriesPerRegion().get("North"));
        assertEquals("Apparel", result.getTopCategoriesPerRegion().get("South"));

        // Nested Map contents
        assertEquals(25000L, result.getRegionCategorySales().get("North").get("Electronics"));
        assertEquals(5000L, result.getRegionCategorySales().get("North").get("Apparel"));
        assertEquals(20000L, result.getRegionCategorySales().get("South").get("Electronics"));
        assertEquals(30000L, result.getRegionCategorySales().get("South").get("Apparel"));
    }
}
