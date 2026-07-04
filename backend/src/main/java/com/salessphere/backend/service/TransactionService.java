package com.salessphere.backend.service;

import com.salessphere.backend.dto.CsvImportResultDto;
import com.salessphere.backend.dto.TransactionRequestDto;
import com.salessphere.backend.dto.TransactionResponseDto;
import com.salessphere.backend.entity.Category;
import com.salessphere.backend.entity.Region;
import com.salessphere.backend.entity.Transaction;
import com.salessphere.backend.entity.User;
import com.salessphere.backend.repository.CategoryRepository;
import com.salessphere.backend.repository.RegionRepository;
import com.salessphere.backend.repository.TransactionRepository;
import com.salessphere.backend.repository.TransactionSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final CsvImportService csvImportService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public Page<TransactionResponseDto> getTransactions(
            String search, String region, String state, String category,
            LocalDate startDate, LocalDate endDate,
            BigDecimal minAmount, BigDecimal maxAmount,
            Pageable pageable) {

        Long minCents = minAmount != null ? minAmount.multiply(BigDecimal.valueOf(100)).longValue() : null;
        Long maxCents = maxAmount != null ? maxAmount.multiply(BigDecimal.valueOf(100)).longValue() : null;

        Specification<Transaction> spec = TransactionSpecification.getFilterSpecification(
                search, region, state, category, startDate, endDate, minCents, maxCents
        );

        return transactionRepository.findAll(spec, pageable).map(this::mapToResponseDto);
    }

    @Transactional(readOnly = true)
    public Optional<TransactionResponseDto> getTransactionById(Long id) {
        return transactionRepository.findById(id).map(this::mapToResponseDto);
    }

    @Transactional
    public TransactionResponseDto createTransaction(TransactionRequestDto request, User user) {
        log.info("Creating manual transaction: {}", request.getTransactionCode());

        if (transactionRepository.existsByTransactionCode(request.getTransactionCode())) {
            throw new IllegalArgumentException("Transaction code '" + request.getTransactionCode() + "' already exists.");
        }

        String stateName = request.getState();
        if (stateName == null || stateName.trim().isEmpty()) {
            stateName = "Unknown";
        }
        String normalizedState = normalizeDimensionName(stateName);
        
        String regionName = "Unknown";
        if (!"Unknown".equalsIgnoreCase(normalizedState)) {
            String lookupKey = normalizedState.toLowerCase();
            if (com.salessphere.backend.service.CsvImportService.STATE_TO_REGION_MAP.containsKey(lookupKey)) {
                regionName = com.salessphere.backend.service.CsvImportService.STATE_TO_REGION_MAP.get(lookupKey);
            }
        }

        Region region = getOrCreateRegion(regionName);
        Category category = getOrCreateCategory(request.getCategoryName());
        long amountCents = request.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
        java.time.LocalDateTime parsedDate = com.salessphere.backend.util.MultiFormatDateParser.parse(request.getTransactionDate());

        Transaction transaction = Transaction.builder()
                .transactionCode(request.getTransactionCode().trim())
                .transactionDate(parsedDate)
                .region(region)
                .state(normalizedState)
                .category(category)
                .amountCents(amountCents)
                .createdBy(user)
                .product(request.getProduct())
                .quantity(request.getQuantity())
                .unitPriceCents(request.getUnitPrice() != null ? request.getUnitPrice().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue() : null)
                .paymentMethod(request.getPaymentMethod())
                .status(request.getStatus())
                .customerId(request.getCustomerId())
                .employeeId(request.getEmployeeId())
                .storeId(request.getStoreId())
                .remarks(request.getRemarks())
                .build();

        Transaction saved = transactionRepository.save(transaction);
        
        auditLogService.logAction(
                user.getUsername(),
                "TRANSACTION_CREATE",
                String.format("Created transaction: %s, State: %s, Derived Region: %s, Category: %s, Amount: $%s",
                        saved.getTransactionCode(), normalizedState, region.getName(), category.getName(), request.getAmount())
        );

        return mapToResponseDto(saved);
    }

    @Transactional
    public TransactionResponseDto updateTransaction(Long id, TransactionRequestDto request, User user) {
        log.info("Updating transaction ID: {}", id);

        Transaction existing = transactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found with id: " + id));

        // Check code uniqueness if code changed
        if (!existing.getTransactionCode().equalsIgnoreCase(request.getTransactionCode()) &&
                transactionRepository.existsByTransactionCode(request.getTransactionCode())) {
            throw new IllegalArgumentException("Transaction code '" + request.getTransactionCode() + "' already exists.");
        }

        String stateName = request.getState();
        if (stateName == null || stateName.trim().isEmpty()) {
            stateName = "Unknown";
        }
        String normalizedState = normalizeDimensionName(stateName);
        
        String regionName = "Unknown";
        if (!"Unknown".equalsIgnoreCase(normalizedState)) {
            String lookupKey = normalizedState.toLowerCase();
            if (com.salessphere.backend.service.CsvImportService.STATE_TO_REGION_MAP.containsKey(lookupKey)) {
                regionName = com.salessphere.backend.service.CsvImportService.STATE_TO_REGION_MAP.get(lookupKey);
            }
        }

        Region region = getOrCreateRegion(regionName);
        Category category = getOrCreateCategory(request.getCategoryName());
        long amountCents = request.getAmount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
        java.time.LocalDateTime parsedDate = com.salessphere.backend.util.MultiFormatDateParser.parse(request.getTransactionDate());

        existing.setTransactionCode(request.getTransactionCode().trim());
        existing.setTransactionDate(parsedDate);
        existing.setRegion(region);
        existing.setState(normalizedState);
        existing.setCategory(category);
        existing.setAmountCents(amountCents);
        
        existing.setProduct(request.getProduct());
        existing.setQuantity(request.getQuantity());
        existing.setUnitPriceCents(request.getUnitPrice() != null ? request.getUnitPrice().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue() : null);
        existing.setPaymentMethod(request.getPaymentMethod());
        existing.setStatus(request.getStatus());
        existing.setCustomerId(request.getCustomerId());
        existing.setEmployeeId(request.getEmployeeId());
        existing.setStoreId(request.getStoreId());
        existing.setRemarks(request.getRemarks());

        Transaction updated = transactionRepository.save(existing);

        auditLogService.logAction(
                user.getUsername(),
                "TRANSACTION_UPDATE",
                String.format("Updated transaction ID %d: %s, State: %s, Derived Region: %s, Category: %s, Amount: $%s",
                        id, updated.getTransactionCode(), normalizedState, region.getName(), category.getName(), request.getAmount())
        );

        return mapToResponseDto(updated);
    }

    @Transactional
    public void deleteTransaction(Long id, User user) {
        log.info("Deleting transaction ID: {}", id);
        Transaction existing = transactionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found with id: " + id));

        transactionRepository.delete(existing);

        auditLogService.logAction(
                user.getUsername(),
                "TRANSACTION_DELETE",
                String.format("Deleted transaction: %s, Region: %s, Category: %s, Amount: $%s",
                        existing.getTransactionCode(), existing.getRegion().getName(), 
                        existing.getCategory().getName(), BigDecimal.valueOf(existing.getAmountCents()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
        );
    }

    @Transactional
    public void deleteAllTransactions(User user) {
        log.info("Bulk deletion of all transactions initiated by: {}", user.getUsername());
        transactionRepository.deleteAll();

        auditLogService.logAction(
                user.getUsername(),
                "TRANSACTION_DELETE_ALL",
                "Bulk deleted all transaction entries from the ledger database."
        );
    }

    @Transactional
    public CsvImportResultDto importCsv(InputStream inputStream, User user, String duplicateAction) {
        return csvImportService.importCsv(inputStream, user, duplicateAction);
    }

    @Transactional
    public CsvImportResultDto importCsv(
            InputStream inputStream, 
            User user, 
            String duplicateAction, 
            String missingRegionPolicy, 
            String missingCategoryPolicy) {
        return csvImportService.importCsv(inputStream, user, duplicateAction, missingRegionPolicy, missingCategoryPolicy);
    }

    private Region getOrCreateRegion(String name) {
        String trimmed = name.trim();
        return regionRepository.findByNameIgnoreCase(trimmed)
                .orElseGet(() -> regionRepository.save(Region.builder().name(trimmed).build()));
    }

    private Category getOrCreateCategory(String name) {
        String trimmed = name.trim();
        return categoryRepository.findByNameIgnoreCase(trimmed)
                .orElseGet(() -> categoryRepository.save(Category.builder().name(trimmed).build()));
    }

    private TransactionResponseDto mapToResponseDto(Transaction transaction) {
        return TransactionResponseDto.builder()
                .id(transaction.getId())
                .transactionCode(transaction.getTransactionCode())
                .transactionDate(transaction.getTransactionDate())
                .regionName(transaction.getRegion().getName())
                .state(transaction.getState())
                .categoryName(transaction.getCategory().getName())
                .amount(BigDecimal.valueOf(transaction.getAmountCents()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .amountCents(transaction.getAmountCents())
                .createdByUsername(transaction.getCreatedBy() != null ? transaction.getCreatedBy().getUsername() : "SYSTEM")
                .product(transaction.getProduct())
                .quantity(transaction.getQuantity())
                .unitPrice(transaction.getUnitPriceCents() != null ? BigDecimal.valueOf(transaction.getUnitPriceCents()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP) : null)
                .paymentMethod(transaction.getPaymentMethod())
                .status(transaction.getStatus())
                .customerId(transaction.getCustomerId())
                .employeeId(transaction.getEmployeeId())
                .storeId(transaction.getStoreId())
                .remarks(transaction.getRemarks())
                .build();
    }

    private String normalizeDimensionName(String name) {
        if (name == null) return "";
        String clean = name.trim();
        if (clean.isEmpty()) return "";
        
        String[] words = clean.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            if (w.contains("-")) {
                String[] parts = w.split("-");
                StringBuilder subSb = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) subSb.append("-");
                    if (!parts[i].isEmpty()) {
                        subSb.append(Character.toUpperCase(parts[i].charAt(0)));
                        if (parts[i].length() > 1) {
                            subSb.append(parts[i].substring(1).toLowerCase());
                        }
                    }
                }
                sb.append(subSb.toString());
            } else {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) {
                    sb.append(w.substring(1).toLowerCase());
                }
            }
        }
        return sb.toString();
    }
}
