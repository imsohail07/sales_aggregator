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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
    

    // Dictionary of field mapping synonyms
    private static final Map<String, List<String>> SYNONYMS_MAP = new LinkedHashMap<>();
    static {
        SYNONYMS_MAP.put("transaction_code", Arrays.asList(
            "transactioncode", "transactionid", "txnid", "invoiceno", "invoicenumber", "billno", "orderid", "receiptid", "saleid"
        ));
        SYNONYMS_MAP.put("transaction_date", Arrays.asList(
            "transactiondate", "date", "saledate", "purchasedate", "orderdate", "invoicedate", "billingdate", "createdat"
        ));
        SYNONYMS_MAP.put("region", Arrays.asList(
            "region", "salesregion", "zone", "territory", "area", "branchregion", "location", "market"
        ));
        SYNONYMS_MAP.put("category", Arrays.asList(
            "category", "productcategory", "categoryname", "department", "productgroup", "itemcategory"
        ));
        SYNONYMS_MAP.put("amount", Arrays.asList(
            "amount", "totalamount", "salesamount", "revenue", "salevalue", "total", "netamount", "grandtotal"
        ));
        
        // Optional Columns Synonyms Mapping
        SYNONYMS_MAP.put("product", Arrays.asList("product", "item", "productname", "itemname"));
        SYNONYMS_MAP.put("quantity", Arrays.asList("quantity", "qty", "units", "count"));
        SYNONYMS_MAP.put("unit_price", Arrays.asList("unitprice", "rate", "priceperunit"));
        SYNONYMS_MAP.put("payment_method", Arrays.asList("paymentmethod", "payment", "paymode", "type"));
        SYNONYMS_MAP.put("status", Arrays.asList("status", "state"));
        SYNONYMS_MAP.put("customer_id", Arrays.asList("customerid", "custid", "clientid"));
        SYNONYMS_MAP.put("employee_id", Arrays.asList("employeeid", "empid", "salespersonid", "staffid"));
        SYNONYMS_MAP.put("store_id", Arrays.asList("storeid", "store", "branchid", "branch", "warehouseid", "warehouse"));
        SYNONYMS_MAP.put("remarks", Arrays.asList("remarks", "remark", "notes", "comment", "comments", "description"));
    }

    @Transactional
    public CsvImportResultDto importCsv(InputStream inputStream, User user) {
        return importCsv(inputStream, user, "SKIP"); // Default fallback
    }

    @Transactional
    public CsvImportResultDto importCsv(InputStream inputStream, User user, String duplicateAction) {
        long startTime = System.currentTimeMillis();
        log.info("Starting Smart CSV Import. User={}, Policy={}", user.getUsername(), duplicateAction);
        
        CsvImportResultDto result = new CsvImportResultDto();
        Map<String, Region> regionCache = new HashMap<>();
        Map<String, Category> categoryCache = new HashMap<>();

        regionRepository.findAll().forEach(r -> regionCache.put(r.getName().toLowerCase(), r));
        categoryRepository.findAll().forEach(c -> categoryCache.put(c.getName().toLowerCase(), c));

        Set<String> processedCodesInFile = new HashSet<>();
        List<Transaction> transactionsToSave = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                result.setStatus("FAILED");
                result.getErrors().add(new CsvImportResultDto.ValidationError(0, "", "CSV file is empty"));
                return result;
            }

            // Normalizing and resolving headers
            String[] rawHeaders = parseCsvLine(headerLine);
            Map<String, Integer> fieldMapping = new HashMap<>();
            
            for (int i = 0; i < rawHeaders.length; i++) {
                String standardField = resolveStandardField(rawHeaders[i]);
                if (standardField != null) {
                    fieldMapping.put(standardField, i);
                } else {
                    String cleanName = rawHeaders[i].replace("\"", "").trim();
                    result.getIgnoredColumns().add(cleanName);
                    result.setIgnoredColumnsCount(result.getIgnoredColumnsCount() + 1);
                }
            }

            // Validate required headers
            String[] requiredFields = {"transaction_code", "transaction_date", "region", "category", "amount"};
            for (String req : requiredFields) {
                if (!fieldMapping.containsKey(req)) {
                    result.setStatus("FAILED");
                    result.getErrors().add(new CsvImportResultDto.ValidationError(1, headerLine, "Missing required column representing: " + req));
                    return result;
                }
            }

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    result.setSkippedRecords(result.getSkippedRecords() + 1);
                    continue;
                }

                result.setTotalRecords(result.getTotalRecords() + 1);
                String[] fields = parseCsvLine(line);

                try {
                    String code = getFieldValue(fields, fieldMapping, "transaction_code");
                    String dateStr = getFieldValue(fields, fieldMapping, "transaction_date");
                    String regionName = getFieldValue(fields, fieldMapping, "region");
                    String categoryName = getFieldValue(fields, fieldMapping, "category");
                    String amountStr = getFieldValue(fields, fieldMapping, "amount");

                    // Validation checks
                    if (code.isEmpty() || dateStr.isEmpty() || regionName.isEmpty() || categoryName.isEmpty() || amountStr.isEmpty()) {
                        throw new IllegalArgumentException("Required columns must not contain empty or null values");
                    }

                    // Duplicate resolution
                    boolean isDuplicate = false;
                    boolean shouldUpdate = false;
                    
                    if (processedCodesInFile.contains(code.toLowerCase())) {
                        isDuplicate = true;
                    } else if (transactionRepository.existsByTransactionCode(code)) {
                        isDuplicate = true;
                        if ("UPDATE".equalsIgnoreCase(duplicateAction)) {
                            shouldUpdate = true;
                        }
                    }

                    if (isDuplicate) {
                        processedCodesInFile.add(code.toLowerCase());
                        if ("REJECT".equalsIgnoreCase(duplicateAction)) {
                            throw new IllegalArgumentException("Duplicate transaction code detected: " + code);
                        } else if ("SKIP".equalsIgnoreCase(duplicateAction) || !shouldUpdate) {
                            result.setDuplicateRecords(result.getDuplicateRecords() + 1);
                            result.getErrors().add(new CsvImportResultDto.ValidationError(lineNumber, line, "Skipped: Duplicate transaction code " + code));
                            continue;
                        }
                    }

                    // Date parsing attempts
                    LocalDateTime date = com.salessphere.backend.util.MultiFormatDateParser.parse(dateStr);

                    // Amount parsing (convert to cents)
                    long amountCents;
                    try {
                        BigDecimal decimalVal = new BigDecimal(amountStr);
                        if (decimalVal.compareTo(BigDecimal.ZERO) < 0) {
                            throw new IllegalArgumentException("Amount must be a positive value");
                        }
                        amountCents = decimalVal.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Malformed decimal format for amount: '" + amountStr + "'");
                    }

                    // Resolve region
                    String regionKey = regionName.toLowerCase();
                    Region region = regionCache.get(regionKey);
                    if (region == null) {
                        region = Region.builder().name(regionName).build();
                        region = regionRepository.save(region);
                        regionCache.put(regionKey, region);
                    }

                    // Resolve category
                    String categoryKey = categoryName.toLowerCase();
                    Category category = categoryCache.get(categoryKey);
                    if (category == null) {
                        category = Category.builder().name(categoryName).build();
                        category = categoryRepository.save(category);
                        categoryCache.put(categoryKey, category);
                    }

                    // Resolve optional columns
                    String product = getOptionalFieldValue(fields, fieldMapping, "product");
                    Integer quantity = getOptionalFieldInt(fields, fieldMapping, "quantity");
                    Long unitPriceCents = getOptionalFieldCents(fields, fieldMapping, "unit_price");
                    String paymentMethod = getOptionalFieldValue(fields, fieldMapping, "payment_method");
                    String status = getOptionalFieldValue(fields, fieldMapping, "status");
                    String customerId = getOptionalFieldValue(fields, fieldMapping, "customer_id");
                    String employeeId = getOptionalFieldValue(fields, fieldMapping, "employee_id");
                    String storeId = getOptionalFieldValue(fields, fieldMapping, "store_id");
                    String remarks = getOptionalFieldValue(fields, fieldMapping, "remarks");

                    if (shouldUpdate) {
                        // Load existing row and modify
                        Transaction existingTx = transactionRepository.findByTransactionCode(code)
                                .orElseThrow(() -> new IllegalStateException("Database sync failed for code: " + code));
                        existingTx.setTransactionDate(date);
                        existingTx.setRegion(region);
                        existingTx.setCategory(category);
                        existingTx.setAmountCents(amountCents);
                        
                        existingTx.setProduct(product);
                        existingTx.setQuantity(quantity);
                        existingTx.setUnitPriceCents(unitPriceCents);
                        existingTx.setPaymentMethod(paymentMethod);
                        existingTx.setStatus(status);
                        existingTx.setCustomerId(customerId);
                        existingTx.setEmployeeId(employeeId);
                        existingTx.setStoreId(storeId);
                        existingTx.setRemarks(remarks);
                        
                        transactionRepository.save(existingTx);
                        result.setDuplicateRecords(result.getDuplicateRecords() + 1); // Tracked under duplicates
                        result.setImportedRecords(result.getImportedRecords() + 1);
                    } else {
                        // Standard creation
                        Transaction tx = Transaction.builder()
                                .transactionCode(code)
                                .transactionDate(date)
                                .region(region)
                                .category(category)
                                .amountCents(amountCents)
                                .createdBy(user)
                                .product(product)
                                .quantity(quantity)
                                .unitPriceCents(unitPriceCents)
                                .paymentMethod(paymentMethod)
                                .status(status)
                                .customerId(customerId)
                                .employeeId(employeeId)
                                .storeId(storeId)
                                .remarks(remarks)
                                .build();

                        transactionsToSave.add(tx);
                        processedCodesInFile.add(code.toLowerCase());
                        result.setImportedRecords(result.getImportedRecords() + 1);

                        if (transactionsToSave.size() >= BATCH_SIZE) {
                            transactionRepository.saveAll(transactionsToSave);
                            transactionsToSave.clear();
                        }
                    }

                } catch (Exception e) {
                    result.setFailedRecords(result.getFailedRecords() + 1);
                    result.getErrors().add(new CsvImportResultDto.ValidationError(lineNumber, line, e.getMessage()));
                }
            }

            // Save remainder
            if (!transactionsToSave.isEmpty()) {
                transactionRepository.saveAll(transactionsToSave);
            }

            long endTime = System.currentTimeMillis();
            long processingTime = endTime - startTime;
            double speed = processingTime > 0 
                    ? (double) result.getTotalRecords() * 1000.0 / processingTime 
                    : result.getTotalRecords();

            result.setProcessingTimeMs(processingTime);
            result.setAverageSpeedRecordsPerSec(roundDouble(speed));
            
            // Set final status
            if (result.getFailedRecords() == 0) {
                result.setStatus("SUCCESS");
            } else if (result.getImportedRecords() > 0) {
                result.setStatus("PARTIAL_SUCCESS");
            } else {
                result.setStatus("FAILED");
            }

            log.info("Smart Import complete. Status={}, Total={}, Imported={}, Duplicates={}, Failed={}, Time={}ms",
                    result.getStatus(), result.getTotalRecords(), result.getImportedRecords(), 
                    result.getDuplicateRecords(), result.getFailedRecords(), processingTime);

            auditLogService.logAction(
                    user.getUsername(),
                    "SMART_CSV_IMPORT",
                    String.format("Imported %d rows, skipped %d duplicates, failed %d rows. Time: %dms, Policy: %s",
                            result.getImportedRecords(), result.getDuplicateRecords(), result.getFailedRecords(), 
                            processingTime, duplicateAction)
            );

        } catch (Exception e) {
            log.error("CSV engine failure", e);
            result.setStatus("FAILED");
            result.getErrors().add(new CsvImportResultDto.ValidationError(0, "", "Critical engine failure: " + e.getMessage()));
        }

        return result;
    }

    private String normalizeHeader(String header) {
        if (header == null) return "";
        return header.toLowerCase()
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .trim();
    }

    private String resolveStandardField(String header) {
        String normalized = normalizeHeader(header);
        for (Map.Entry<String, List<String>> entry : SYNONYMS_MAP.entrySet()) {
            String target = entry.getKey();
            if (target.replace("_", "").equals(normalized) || entry.getValue().contains(normalized)) {
                return target;
            }
        }
        return null;
    }

    private String[] parseCsvLine(String line) {
        // Splitting logic respecting quoted commas
        return line.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
    }

    private String getFieldValue(String[] fields, Map<String, Integer> mapping, String key) {
        Integer index = mapping.get(key);
        if (index == null || index >= fields.length) {
            return "";
        }
        return fields[index].replace("\"", "").trim();
    }

    private String getOptionalFieldValue(String[] fields, Map<String, Integer> mapping, String key) {
        String val = getFieldValue(fields, mapping, key);
        return val.isEmpty() ? null : val;
    }

    private Integer getOptionalFieldInt(String[] fields, Map<String, Integer> mapping, String key) {
        String val = getFieldValue(fields, mapping, key);
        if (val.isEmpty()) return null;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long getOptionalFieldCents(String[] fields, Map<String, Integer> mapping, String key) {
        String val = getFieldValue(fields, mapping, key);
        if (val.isEmpty()) return null;
        try {
            BigDecimal dec = new BigDecimal(val);
            return dec.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    private double roundDouble(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
