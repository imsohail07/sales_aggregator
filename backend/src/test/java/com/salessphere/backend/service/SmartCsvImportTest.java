package com.salessphere.backend.service;

import com.salessphere.backend.dto.CsvImportResultDto;
import com.salessphere.backend.entity.Category;
import com.salessphere.backend.entity.Region;
import com.salessphere.backend.entity.Transaction;
import com.salessphere.backend.entity.User;
import com.salessphere.backend.repository.CategoryRepository;
import com.salessphere.backend.repository.RegionRepository;
import com.salessphere.backend.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SmartCsvImportTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private CsvImportService csvImportService;

    private User mockUser;
    private Region mockRegion;
    private Category mockCategory;

    @BeforeEach
    public void setUp() {
        mockUser = User.builder().username("admin").build();
        mockRegion = Region.builder().id(1L).name("West").build();
        mockCategory = Category.builder().id(1L).name("Apparel").build();
    }

    @Test
    public void testSmartHeaderMapping_AndOptionalFields() {
        // Complex casing, hyphens, spaces, underscores, and synonyms
        String csvContent = "Invoice No,invoice-date,Sales_Region,ProductGroup,grand_total,paymode,notes,unknown_col_1\n" +
                "TXN999,2026-07-01,West,Apparel,250.75,Credit Card,Special Promo,IgnoredData\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        when(regionRepository.findAll()).thenReturn(Collections.singletonList(mockRegion));
        when(categoryRepository.findAll()).thenReturn(Collections.singletonList(mockCategory));
        when(transactionRepository.existsByTransactionCode(anyString())).thenReturn(false);

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser, "SKIP");

        assertEquals(1, result.getTotalRecords());
        assertEquals(1, result.getImportedRecords());
        assertEquals(0, result.getFailedRecords());
        
        // Ignored column checking
        assertEquals(1, result.getIgnoredColumnsCount());
        assertTrue(result.getIgnoredColumns().contains("unknown_col_1"));

        // Timing & Speed properties check
        assertTrue(result.getProcessingTimeMs() >= 0);
        assertTrue(result.getAverageSpeedRecordsPerSec() >= 0);
        assertEquals("SUCCESS", result.getStatus());

        verify(transactionRepository, times(1)).saveAll(argThat(list -> {
            Transaction tx = list.iterator().next();
            assertEquals("TXN999", tx.getTransactionCode());
            assertEquals(25075L, tx.getAmountCents());
            assertEquals("West", tx.getRegion().getName());
            assertEquals("Apparel", tx.getCategory().getName());
            
            // Optional fields validation
            assertEquals("Credit Card", tx.getPaymentMethod());
            assertEquals("Special Promo", tx.getRemarks());
            return true;
        }));
    }

    @Test
    public void testDuplicatePolicy_Update() {
        String csvContent = "txn_id,date,region,category,amount,remarks\n" +
                "TXN_DUP,2026-07-01,West,Apparel,400.00,Updated Remarks\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        Transaction existingTx = Transaction.builder()
                .id(100L)
                .transactionCode("TXN_DUP")
                .amountCents(10000L)
                .region(mockRegion)
                .category(mockCategory)
                .build();

        when(regionRepository.findAll()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
        when(transactionRepository.existsByTransactionCode("TXN_DUP")).thenReturn(true);
        when(transactionRepository.findByTransactionCode("TXN_DUP")).thenReturn(Optional.of(existingTx));
        when(regionRepository.save(any(Region.class))).thenAnswer(i -> i.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser, "UPDATE");

        assertEquals(1, result.getTotalRecords());
        assertEquals(1, result.getImportedRecords());
        assertEquals(1, result.getDuplicateRecords()); // Duplicate count is incremented
        assertEquals(0, result.getFailedRecords());

        // Verify update save is called directly on entity
        verify(transactionRepository, times(1)).save(existingTx);
        assertEquals(40000L, existingTx.getAmountCents()); // Amount updated from 10000 to 40000 cents
        assertEquals("Updated Remarks", existingTx.getRemarks());
    }

    @Test
    public void testDuplicatePolicy_Reject() {
        String csvContent = "txn_id,date,region,category,amount\n" +
                "TXN_REJ,2026-07-01,West,Apparel,150.00\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        when(regionRepository.findAll()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
        when(transactionRepository.existsByTransactionCode("TXN_REJ")).thenReturn(true);

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser, "REJECT");

        assertEquals(1, result.getTotalRecords());
        assertEquals(0, result.getImportedRecords());
        assertEquals(1, result.getFailedRecords()); // Fails under REJECT
        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getErrors().get(0).getErrorMessage().contains("Duplicate transaction code detected"));
    }
}
