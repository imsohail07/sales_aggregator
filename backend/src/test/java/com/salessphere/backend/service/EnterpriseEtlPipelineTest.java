package com.salessphere.backend.service;

import com.salessphere.backend.dto.CsvImportResultDto;
import com.salessphere.backend.entity.Category;
import com.salessphere.backend.entity.Region;
import com.salessphere.backend.entity.Transaction;
import com.salessphere.backend.entity.User;
import com.salessphere.backend.entity.ImportHistory;
import com.salessphere.backend.repository.CategoryRepository;
import com.salessphere.backend.repository.RegionRepository;
import com.salessphere.backend.repository.TransactionRepository;
import com.salessphere.backend.repository.ImportHistoryRepository;
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
public class EnterpriseEtlPipelineTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ImportHistoryRepository importHistoryRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private CsvImportService csvImportService;

    private User mockUser;
    private Region mockRegion;
    private Category mockCategory;

    @BeforeEach
    public void setUp() {
        mockUser = User.builder().username("etl_user").build();
        mockRegion = Region.builder().id(1L).name("North").build();
        mockCategory = Category.builder().id(1L).name("Electronics").build();
    }

    @Test
    public void testMissingRegionPolicy_AssignUnknown() {
        // Line 2 has an empty region field
        String csvContent = "txn_id,date,region,category,amount\n" +
                "TXN001,2026-07-01,,Electronics,150.50\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        when(regionRepository.findAll()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
        when(transactionRepository.existsByTransactionCode("TXN001")).thenReturn(false);
        
        // Save unknown region
        when(regionRepository.save(argThat(r -> "Unknown".equals(r.getName())))).thenAnswer(i -> i.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser, "SKIP", "ASSIGN_UNKNOWN", "CREATE_AUTOMATIC");

        assertEquals(1, result.getTotalRecords());
        assertEquals(1, result.getImportedRecords());
        assertEquals(0, result.getFailedRecords());
        assertEquals("Completed Successfully", result.getStatus());

        verify(importHistoryRepository, times(1)).save(any(ImportHistory.class));
    }

    @Test
    public void testMissingRegionPolicy_SkipRow() {
        // Line 2 has an empty region field
        String csvContent = "txn_id,date,region,category,amount\n" +
                "TXN001,2026-07-01,,Electronics,150.50\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        when(regionRepository.findAll()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser, "SKIP", "SKIP_ROW", "CREATE_AUTOMATIC");

        assertEquals(1, result.getTotalRecords());
        assertEquals(0, result.getImportedRecords());
        assertEquals(1, result.getFailedRecords());
        assertEquals("Failed", result.getStatus());
        assertTrue(result.getErrors().get(0).getErrorMessage().contains("Region is missing"));
    }

    @Test
    public void testMissingCategoryPolicy_SkipRow() {
        // Category 'Apparel' is new and is not cached/resolved
        String csvContent = "txn_id,date,region,category,amount\n" +
                "TXN001,2026-07-01,North,Apparel,150.50\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        when(regionRepository.findAll()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
        when(regionRepository.save(any(Region.class))).thenAnswer(i -> i.getArgument(0));

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser, "SKIP", "ASSIGN_UNKNOWN", "SKIP_ROW");

        assertEquals(1, result.getTotalRecords());
        assertEquals(0, result.getImportedRecords());
        assertEquals(1, result.getFailedRecords());
        assertTrue(result.getErrors().get(0).getErrorMessage().contains("policy is SKIP_ROW"));
    }

    @Test
    public void testNegativeAmount_RefundAllowed() {
        // Negative amount, but payment_method has 'refund'
        String csvContent = "txn_id,date,region,category,amount,paymode\n" +
                "TXN001,2026-07-01,North,Electronics,-100.50,Refund Via Card\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        when(regionRepository.findAll()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
        when(transactionRepository.existsByTransactionCode("TXN001")).thenReturn(false);
        when(regionRepository.save(any(Region.class))).thenAnswer(i -> i.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser, "SKIP", "ASSIGN_UNKNOWN", "CREATE_AUTOMATIC");

        assertEquals(1, result.getTotalRecords());
        assertEquals(1, result.getImportedRecords());
        assertEquals(0, result.getFailedRecords());

        verify(transactionRepository, times(1)).saveAll(argThat(list -> {
            Transaction tx = list.iterator().next();
            assertEquals(-10050L, tx.getAmountCents());
            assertEquals("REFUND", tx.getStatus());
            return true;
        }));
    }

    @Test
    public void testNegativeAmount_RefundRejected() {
        // Negative amount, no refund keywords in remarks/category/paymode
        String csvContent = "txn_id,date,region,category,amount,paymode\n" +
                "TXN001,2026-07-01,North,Electronics,-100.50,Cash\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        when(regionRepository.findAll()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser, "SKIP", "ASSIGN_UNKNOWN", "CREATE_AUTOMATIC");

        assertEquals(1, result.getTotalRecords());
        assertEquals(0, result.getImportedRecords());
        assertEquals(1, result.getFailedRecords());
        assertTrue(result.getErrors().get(0).getErrorMessage().contains("Negative amount is only allowed"));
    }

    @Test
    public void testDuplicateAction_Replace() {
        String csvContent = "txn_id,date,region,category,amount\n" +
                "TXN001,2026-07-01,North,Electronics,200.00\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        Transaction existingTx = Transaction.builder()
                .id(999L)
                .transactionCode("TXN001")
                .amountCents(15000L)
                .region(mockRegion)
                .category(mockCategory)
                .build();

        when(regionRepository.findAll()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
        when(transactionRepository.existsByTransactionCode("TXN001")).thenReturn(true);
        when(transactionRepository.findByTransactionCode("TXN001")).thenReturn(Optional.of(existingTx));
        when(regionRepository.save(any(Region.class))).thenAnswer(i -> i.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser, "REPLACE", "ASSIGN_UNKNOWN", "CREATE_AUTOMATIC");

        assertEquals(1, result.getTotalRecords());
        assertEquals(1, result.getImportedRecords()); // Deletes old, saves brand new
        assertEquals(1, result.getDuplicateRecords());

        verify(transactionRepository, times(1)).delete(existingTx);
        verify(transactionRepository, times(1)).save(argThat(tx -> {
            assertNotEquals(999L, tx.getId()); // Brand new object (null ID before save)
            assertEquals("TXN001", tx.getTransactionCode());
            assertEquals(20000L, tx.getAmountCents());
            return true;
        }));
    }

    @Test
    public void testDuplicateAction_InsertAsNew() {
        String csvContent = "txn_id,date,region,category,amount\n" +
                "TXN001,2026-07-01,North,Electronics,200.00\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        when(regionRepository.findAll()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
        
        // Mock existence checks to suffix it
        when(transactionRepository.existsByTransactionCode("TXN001")).thenReturn(true);
        when(transactionRepository.existsByTransactionCode("TXN001_1")).thenReturn(false);
        when(regionRepository.save(any(Region.class))).thenAnswer(i -> i.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser, "INSERT_AS_NEW", "ASSIGN_UNKNOWN", "CREATE_AUTOMATIC");

        assertEquals(1, result.getTotalRecords());
        assertEquals(1, result.getImportedRecords());
        assertEquals(0, result.getDuplicateRecords()); // Generates new code, so not counted as skipped/updated duplicate

        verify(transactionRepository, times(1)).saveAll(argThat(list -> {
            Transaction tx = list.iterator().next();
            assertEquals("TXN001_1", tx.getTransactionCode()); // Suffix added
            return true;
        }));
    }
}
