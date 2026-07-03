package com.salessphere.backend.service;

import com.salessphere.backend.dto.CsvImportResultDto;
import com.salessphere.backend.entity.Category;
import com.salessphere.backend.entity.Region;
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
public class CsvImportServiceTest {

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
        mockRegion = Region.builder().id(1L).name("North").build();
        mockCategory = Category.builder().id(1L).name("Electronics").build();
    }

    @Test
    public void testImportCsv_Success() {
        String csvContent = "transaction_code,transaction_date,region,category,amount\n" +
                "TXN001,2026-07-01,North,Electronics,150.50\n" +
                "TXN002,2026-07-02,South,Apparel,99.99\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        // Mock database resolutions
        when(regionRepository.findAll()).thenReturn(Collections.singletonList(mockRegion));
        when(categoryRepository.findAll()).thenReturn(Collections.singletonList(mockCategory));
        when(transactionRepository.existsByTransactionCode(anyString())).thenReturn(false);

        // Mock dynamic save for new regions and categories
        when(regionRepository.save(any(Region.class))).thenAnswer(i -> i.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser);

        assertEquals(2, result.getTotalRecords());
        assertEquals(2, result.getImportedRecords());
        assertEquals(0, result.getDuplicateRecords());
        assertEquals(0, result.getFailedRecords());
        assertTrue(result.getErrors().isEmpty());

        verify(transactionRepository, times(1)).saveAll(anyList());
    }

    @Test
    public void testImportCsv_MissingHeaders() {
        String csvContent = "transaction_code,transaction_date,region,category\n" + // Missing 'amount'
                "TXN001,2026-07-01,North,Electronics\n";

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser);

        assertEquals(0, result.getTotalRecords());
        assertEquals(0, result.getImportedRecords());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).getErrorMessage().contains("Missing required header: amount"));
    }

    @Test
    public void testImportCsv_ValidationFailure_InvalidAmount() {
        String csvContent = "transaction_code,transaction_date,region,category,amount\n" +
                "TXN001,2026-07-01,North,Electronics,-10.00\n" + // Negative amount
                "TXN002,2026-07-02,South,Apparel,invalid_val\n";  // Malformed decimal

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        when(regionRepository.findAll()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
        when(transactionRepository.existsByTransactionCode(anyString())).thenReturn(false);

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser);

        assertEquals(2, result.getTotalRecords());
        assertEquals(0, result.getImportedRecords());
        assertEquals(2, result.getFailedRecords());
        assertEquals(2, result.getErrors().size());
        assertTrue(result.getErrors().get(0).getErrorMessage().contains("Amount must be positive"));
        assertTrue(result.getErrors().get(1).getErrorMessage().contains("Invalid amount decimal format"));
    }

    @Test
    public void testImportCsv_DuplicateDetection() {
        String csvContent = "transaction_code,transaction_date,region,category,amount\n" +
                "TXN001,2026-07-01,North,Electronics,100.00\n" +
                "TXN001,2026-07-01,North,Electronics,100.00\n"; // Duplicate in file

        InputStream inputStream = new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8));

        when(regionRepository.findAll()).thenReturn(Collections.emptyList());
        when(categoryRepository.findAll()).thenReturn(Collections.emptyList());
        when(transactionRepository.existsByTransactionCode("TXN001")).thenReturn(false);
        when(regionRepository.save(any(Region.class))).thenAnswer(i -> i.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        CsvImportResultDto result = csvImportService.importCsv(inputStream, mockUser);

        assertEquals(2, result.getTotalRecords());
        assertEquals(1, result.getImportedRecords());
        assertEquals(1, result.getDuplicateRecords()); // Skips the second TXN001
        assertEquals(0, result.getFailedRecords());
    }
}
