package com.salessphere.backend.service;

import com.salessphere.backend.dto.CsvImportResultDto;
import com.salessphere.backend.entity.Category;
import com.salessphere.backend.entity.Region;
import com.salessphere.backend.entity.Transaction;
import com.salessphere.backend.entity.User;
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
public class SmartCsvImportTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ImportHistoryRepository importHistoryRepository;

    @InjectMocks
    private CsvImportService csvImportService;

    private User mockUser;
    private Region mockRegion;
    private Category mockCategory;

    @BeforeEach
    public void setUp() {
        mockUser = User.builder().username("admin").build();
        mockRegion = Region.builder().id(1L).name("West Coast").build();
        mockCategory = Category.builder().id(1L).name("Apparel Goods").build();
    }

    @Test
    public void testPipeDelimiter_TitleCase_EuropeanCurrency_AndNullValues() {
        String csvContent = "Invoice No|invoice-date|Sales_Region|ProductGroup|grand_total|qty|paymode\n" +
                "TXN888|2026-07-01|west coast|apparel goods| 1.500,75 € |N/A|Credit Card\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        when(regionRepository.findAll()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
        when(transactionRepository.existsByTransactionCode(anyString())).thenReturn(false);
        when(regionRepository.save(any(Region.class))).thenAnswer(i -> i.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser, "SKIP");

        assertEquals(1, result.getTotalRecords());
        assertEquals(1, result.getImportedRecords());
        assertEquals(0, result.getFailedRecords());
        assertEquals("Completed Successfully", result.getStatus());
        assertEquals("UTF-8", result.getDetectedEncoding());
        assertEquals("|", result.getDetectedDelimiter());

        verify(transactionRepository, times(1)).saveAll(argThat(list -> {
            Transaction tx = list.iterator().next();
            assertEquals("TXN888", tx.getTransactionCode());
            assertEquals(150075L, tx.getAmountCents());
            assertEquals("West Coast", tx.getRegion().getName());
            assertEquals("Apparel Goods", tx.getCategory().getName());
            assertNull(tx.getQuantity());
            assertEquals("Credit Card", tx.getPaymentMethod());
            return true;
        }));
    }

    @Test
    public void testDuplicatePolicy_Update() {
        String csvContent = "txn_id,date,region,category,amount,remarks\n" +
                "TXN_DUP,2026-07-01,West Coast,Apparel Goods,400.00,Updated Remarks\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        Transaction existingTx = Transaction.builder()
                .id(100L)
                .transactionCode("TXN_DUP")
                .amountCents(10000L)
                .region(mockRegion)
                .category(mockCategory)
                .build();

        when(regionRepository.findAll()).thenReturn(Collections.singletonList(mockRegion));
        when(categoryRepository.findAll()).thenReturn(Collections.singletonList(mockCategory));
        when(transactionRepository.existsByTransactionCode("TXN_DUP")).thenReturn(true);
        when(transactionRepository.findByTransactionCode("TXN_DUP")).thenReturn(Optional.of(existingTx));

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser, "UPDATE");

        assertEquals(1, result.getTotalRecords());
        assertEquals(0, result.getImportedRecords()); // It is an update, not a new import
        assertEquals(1, result.getUpdatedRecords());
        assertEquals(1, result.getDuplicatesUpdated());
        assertEquals(0, result.getFailedRecords());

        verify(transactionRepository, times(1)).save(existingTx);
        assertEquals(40000L, existingTx.getAmountCents());
        assertEquals("Updated Remarks", existingTx.getRemarks());
    }

    @Test
    public void testDuplicatePolicy_Reject() {
        String csvContent = "txn_id,date,region,category,amount\n" +
                "TXN_REJ,2026-07-01,West Coast,Apparel Goods,150.00\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        when(regionRepository.findAll()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
        when(transactionRepository.existsByTransactionCode("TXN_REJ")).thenReturn(true);

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser, "REJECT");

        assertEquals(1, result.getTotalRecords());
        assertEquals(0, result.getImportedRecords());
        assertEquals(1, result.getFailedRecords());
        assertEquals("Failed", result.getStatus());
        assertTrue(result.getErrors().get(0).getErrorMessage().contains("Duplicate transaction code detected"));
    }
}
