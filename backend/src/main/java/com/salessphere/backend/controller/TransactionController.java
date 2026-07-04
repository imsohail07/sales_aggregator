package com.salessphere.backend.controller;

import com.salessphere.backend.dto.CsvImportResultDto;
import com.salessphere.backend.dto.MessageResponseDto;
import com.salessphere.backend.dto.TransactionRequestDto;
import com.salessphere.backend.dto.TransactionResponseDto;
import com.salessphere.backend.entity.User;
import com.salessphere.backend.repository.UserRepository;
import com.salessphere.backend.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final TransactionService transactionService;
    private final UserRepository userRepository;
    private final com.salessphere.backend.repository.ImportHistoryRepository importHistoryRepository;

    @GetMapping
    public ResponseEntity<Page<TransactionResponseDto>> getTransactions(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "transactionDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        Sort.Direction dir = direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(dir, sortBy));

        Page<TransactionResponseDto> results = transactionService.getTransactions(
                search, region, state, category, startDate, endDate, minAmount, maxAmount, pageable
        );
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponseDto> getTransactionById(@PathVariable Long id) {
        return transactionService.getTransactionById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TransactionResponseDto> createTransaction(
            @Valid @RequestBody TransactionRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        User user = getUser(userDetails);
        TransactionResponseDto created = transactionService.createTransaction(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TransactionResponseDto> updateTransaction(
            @PathVariable Long id,
            @Valid @RequestBody TransactionRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        User user = getUser(userDetails);
        try {
            TransactionResponseDto updated = transactionService.updateTransaction(id, request, user);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponseDto> deleteTransaction(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        User user = getUser(userDetails);
        try {
            transactionService.deleteTransaction(id, user);
            return ResponseEntity.ok(new MessageResponseDto("Transaction deleted successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(e.getMessage()));
        }
    }

    @DeleteMapping("/delete-all")
    public ResponseEntity<MessageResponseDto> deleteAllTransactions(
            @AuthenticationPrincipal UserDetails userDetails) {
        
        User user = getUser(userDetails);
        try {
            transactionService.deleteAllTransactions(user);
            return ResponseEntity.ok(new MessageResponseDto("All transactions deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new MessageResponseDto(e.getMessage()));
        }
    }

    @PostMapping("/import")
    public ResponseEntity<CsvImportResultDto> importCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "SKIP") String duplicateAction,
            @RequestParam(defaultValue = "ASSIGN_UNKNOWN") String missingRegionPolicy,
            @RequestParam(defaultValue = "CREATE_AUTOMATIC") String missingCategoryPolicy,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        if (file.isEmpty()) {
            CsvImportResultDto result = new CsvImportResultDto();
            result.getErrors().add(CsvImportResultDto.ValidationError.builder()
                    .lineNumber(0)
                    .errorMessage("Uploaded file is empty")
                    .severity("ERROR")
                    .build());
            return ResponseEntity.badRequest().body(result);
        }

        User user = getUser(userDetails);
        try {
            CsvImportResultDto result = transactionService.importCsv(
                    file.getInputStream(), user, duplicateAction, missingRegionPolicy, missingCategoryPolicy
            );
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("Failed to parse CSV upload stream", e);
            CsvImportResultDto errResult = new CsvImportResultDto();
            errResult.getErrors().add(CsvImportResultDto.ValidationError.builder()
                    .lineNumber(0)
                    .errorMessage("IO Error reading file: " + e.getMessage())
                    .severity("ERROR")
                    .build());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errResult);
        }
    }

    @GetMapping("/import/history")
    public ResponseEntity<java.util.List<com.salessphere.backend.entity.ImportHistory>> getImportHistory() {
        return ResponseEntity.ok(importHistoryRepository.findAllByOrderByTimestampDesc());
    }

    private User getUser(UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("Current user session not found"));
    }
}
