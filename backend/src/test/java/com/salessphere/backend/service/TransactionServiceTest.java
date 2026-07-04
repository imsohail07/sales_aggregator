package com.salessphere.backend.service;

import com.salessphere.backend.dto.TransactionRequestDto;
import com.salessphere.backend.dto.TransactionResponseDto;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private TransactionService transactionService;

    private User mockUser;
    private Region southRegion;
    private Category electronicsCategory;

    @BeforeEach
    public void setUp() {
        mockUser = User.builder().username("admin").build();
        southRegion = Region.builder().id(1L).name("South").build();
        electronicsCategory = Category.builder().id(1L).name("Electronics").build();
    }

    @Test
    public void testCreateTransaction_DerivesRegionFromState() {
        TransactionRequestDto request = TransactionRequestDto.builder()
                .transactionCode("TXN_TST_001")
                .transactionDate("2026-07-04")
                .state("Karnataka")
                .categoryName("Electronics")
                .amount(BigDecimal.valueOf(150.50))
                .build();

        // Stub existence checks & lookups
        when(transactionRepository.existsByTransactionCode("TXN_TST_001")).thenReturn(false);
        when(regionRepository.findByNameIgnoreCase("South")).thenReturn(Optional.of(southRegion));
        when(categoryRepository.findByNameIgnoreCase("Electronics")).thenReturn(Optional.of(electronicsCategory));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction tx = i.getArgument(0);
            tx.setId(100L); // simulate save id generation
            return tx;
        });

        TransactionResponseDto response = transactionService.createTransaction(request, mockUser);

        assertNotNull(response);
        assertEquals("TXN_TST_001", response.getTransactionCode());
        assertEquals("Karnataka", response.getState());
        assertEquals("South", response.getRegionName());
        assertEquals("Electronics", response.getCategoryName());
        assertEquals(new BigDecimal("150.50"), response.getAmount());

        verify(transactionRepository, times(1)).save(any(Transaction.class));
        verify(auditLogService, times(1)).logAction(eq("admin"), eq("TRANSACTION_CREATE"), anyString());
    }

    @Test
    public void testUpdateTransaction_DerivesNewRegionFromNewState() {
        Transaction existingTx = Transaction.builder()
                .id(100L)
                .transactionCode("TXN_TST_001")
                .transactionDate(LocalDateTime.now())
                .state("Karnataka")
                .region(southRegion)
                .category(electronicsCategory)
                .amountCents(15050L)
                .build();

        TransactionRequestDto request = TransactionRequestDto.builder()
                .transactionCode("TXN_TST_001")
                .transactionDate("2026-07-04")
                .state("Delhi") // South -> North
                .categoryName("Electronics")
                .amount(BigDecimal.valueOf(250.00))
                .build();

        Region northRegion = Region.builder().id(2L).name("North").build();

        when(transactionRepository.findById(100L)).thenReturn(Optional.of(existingTx));
        when(regionRepository.findByNameIgnoreCase("North")).thenReturn(Optional.of(northRegion));
        when(categoryRepository.findByNameIgnoreCase("Electronics")).thenReturn(Optional.of(electronicsCategory));

        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        TransactionResponseDto response = transactionService.updateTransaction(100L, request, mockUser);

        assertNotNull(response);
        assertEquals("Delhi", response.getState());
        assertEquals("North", response.getRegionName());
        assertEquals(new BigDecimal("250.00"), response.getAmount());

        verify(transactionRepository, times(1)).save(existingTx);
        verify(auditLogService, times(1)).logAction(eq("admin"), eq("TRANSACTION_UPDATE"), anyString());
    }
}
