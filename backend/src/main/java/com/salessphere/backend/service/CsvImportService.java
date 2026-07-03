package com.salessphere.backend.service;

import com.salessphere.backend.dto.CsvImportResultDto;
import com.salessphere.backend.entity.Category;
import com.salessphere.backend.entity.Region;
import com.salessphere.backend.entity.Transaction;
import com.salessphere.backend.entity.User;
import com.salessphere.backend.repository.CategoryRepository;
import com.salessphere.backend.repository.RegionRepository;
import com.salessphere.backend.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportService {

    private final TransactionRepository transactionRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    private static final int BATCH_SIZE = 1000;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Transactional
    public CsvImportResultDto importCsv(InputStream inputStream, User user) {
        log.info("Starting CSV import initiated by user: {}", user.getUsername());
        CsvImportResultDto result = new CsvImportResultDto();

        // Local cache of regions and categories to avoid constant DB queries
        Map<String, Region> regionCache = new HashMap<>();
        Map<String, Category> categoryCache = new HashMap<>();
        
        regionRepository.findAll().forEach(r -> regionCache.put(r.getName().toLowerCase(), r));
        categoryRepository.findAll().forEach(c -> categoryCache.put(c.getName().toLowerCase(), c));

        // Keep track of transaction codes processed in this file to find duplicates
        Set<String> processedCodes = new HashSet<>();
        List<Transaction> transactionsToSave = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                result.getErrors().add(new CsvImportResultDto.ValidationError(0, "", "CSV file is empty"));
                return result;
            }

            // Parse headers
            String[] headers = parseCsvLine(headerLine);
            Map<String, Integer> headerIndexMap = mapHeaders(headers);
            
            // Validate required headers
            String[] required = {"transaction_code", "transaction_date", "region", "category", "amount"};
            for (String req : required) {
                if (!headerIndexMap.containsKey(req)) {
                    result.getErrors().add(new CsvImportResultDto.ValidationError(1, headerLine, "Missing required header: " + req));
                    return result;
                }
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }

                result.setTotalRecords(result.getTotalRecords() + 1);
                String[] fields = parseCsvLine(line);

                try {
                    // Extract values using header map
                    String code = getFieldValue(fields, headerIndexMap, "transaction_code", lineNumber);
                    String dateStr = getFieldValue(fields, headerIndexMap, "transaction_date", lineNumber);
                    String regionName = getFieldValue(fields, headerIndexMap, "region", lineNumber);
                    String categoryName = getFieldValue(fields, headerIndexMap, "category", lineNumber);
                    String amountStr = getFieldValue(fields, headerIndexMap, "amount", lineNumber);

                    // Validations
                    if (code.isEmpty() || dateStr.isEmpty() || regionName.isEmpty() || categoryName.isEmpty() || amountStr.isEmpty()) {
                        throw new IllegalArgumentException("Fields must not be empty");
                    }

                    // Duplicate detection in file
                    if (processedCodes.contains(code.toLowerCase())) {
                        result.setDuplicateRecords(result.getDuplicateRecords() + 1);
                        continue;
                    }

                    // Duplicate detection in database
                    if (transactionRepository.existsByTransactionCode(code)) {
                        result.setDuplicateRecords(result.getDuplicateRecords() + 1);
                        processedCodes.add(code.toLowerCase());
                        continue;
                    }

                    // Date parsing
                    LocalDate date;
                    try {
                        date = LocalDate.parse(dateStr, DATE_FORMATTER);
                    } catch (DateTimeParseException e) {
                        throw new IllegalArgumentException("Invalid date format. Expected yyyy-MM-dd");
                    }

                    // Amount parsing (convert to cents)
                    long amountCents;
                    try {
                        BigDecimal decimalVal = new BigDecimal(amountStr);
                        if (decimalVal.compareTo(BigDecimal.ZERO) < 0) {
                            throw new IllegalArgumentException("Amount must be positive");
                        }
                        amountCents = decimalVal.multiply(BigDecimal.valueOf(100)).setScale(0, BigDecimal.ROUND_HALF_UP).longValue();
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid amount decimal format");
                    }

                    // Region resolution (dynamic insert if missing)
                    String regionKey = regionName.trim().toLowerCase();
                    Region region = regionCache.get(regionKey);
                    if (region == null) {
                        region = Region.builder().name(regionName.trim()).build();
                        region = regionRepository.save(region);
                        regionCache.put(regionKey, region);
                    }

                    // Category resolution (dynamic insert if missing)
                    String categoryKey = categoryName.trim().toLowerCase();
                    Category category = categoryCache.get(categoryKey);
                    if (category == null) {
                        category = Category.builder().name(categoryName.trim()).build();
                        category = categoryRepository.save(category);
                        categoryCache.put(categoryKey, category);
                    }

                    // Build transaction entity
                    Transaction transaction = Transaction.builder()
                            .transactionCode(code)
                            .transactionDate(date)
                            .region(region)
                            .category(category)
                            .amountCents(amountCents)
                            .createdBy(user)
                            .build();

                    transactionsToSave.add(transaction);
                    processedCodes.add(code.toLowerCase());
                    result.setImportedRecords(result.getImportedRecords() + 1);

                    // Batch save to database to control memory and keep performance high
                    if (transactionsToSave.size() >= BATCH_SIZE) {
                        transactionRepository.saveAll(transactionsToSave);
                        transactionsToSave.clear();
                    }

                } catch (Exception e) {
                    result.setFailedRecords(result.getFailedRecords() + 1);
                    result.getErrors().add(new CsvImportResultDto.ValidationError(lineNumber, line, e.getMessage()));
                }
            }

            // Save remaining records
            if (!transactionsToSave.isEmpty()) {
                transactionRepository.saveAll(transactionsToSave);
            }

            log.info("CSV import completed: Total={}, Imported={}, Duplicates={}, Failed={}",
                    result.getTotalRecords(), result.getImportedRecords(), result.getDuplicateRecords(), result.getFailedRecords());

            auditLogService.logAction(
                    user.getUsername(),
                    "CSV_IMPORT",
                    String.format("Imported %d transactions successfully, skipped %d duplicates, failed %d records",
                            result.getImportedRecords(), result.getDuplicateRecords(), result.getFailedRecords())
            );

        } catch (Exception e) {
            log.error("Critical error reading CSV file", e);
            result.getErrors().add(new CsvImportResultDto.ValidationError(0, "", "Critical error reading file: " + e.getMessage()));
        }

        return result;
    }

    private String[] parseCsvLine(String line) {
        // Regex to split by commas outside double quotes
        return line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
    }

    private Map<String, Integer> mapHeaders(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            // Trim quotes and whitespace
            String cleanHeader = headers[i].replace("\"", "").trim().toLowerCase();
            map.put(cleanHeader, i);
        }
        return map;
    }

    private String getFieldValue(String[] fields, Map<String, Integer> indexMap, String header, int line) {
        Integer index = indexMap.get(header);
        if (index == null || index >= fields.length) {
            throw new IllegalArgumentException("Missing value for column: " + header);
        }
        return fields[index].replace("\"", "").trim();
    }
}
