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
import com.salessphere.backend.util.MultiFormatDateParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportService {

    private final TransactionRepository transactionRepository;
    private final RegionRepository regionRepository;
    private final CategoryRepository categoryRepository;
    private final ImportHistoryRepository importHistoryRepository;
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
        return importCsv(inputStream, user, "SKIP", "ASSIGN_UNKNOWN", "CREATE_AUTOMATIC");
    }

    @Transactional
    public CsvImportResultDto importCsv(InputStream inputStream, User user, String duplicateAction) {
        return importCsv(inputStream, user, duplicateAction, "ASSIGN_UNKNOWN", "CREATE_AUTOMATIC");
    }

    @Transactional
    public CsvImportResultDto importCsv(
            InputStream inputStream, 
            User user, 
            String duplicateAction, 
            String missingRegionPolicy, 
            String missingCategoryPolicy) {
        
        long startTime = System.currentTimeMillis();
        log.info("Starting Enterprise ETL Pipeline. User={}, Policy={}, RegionPolicy={}, CategoryPolicy={}", 
                user.getUsername(), duplicateAction, missingRegionPolicy, missingCategoryPolicy);
        
        CsvImportResultDto result = new CsvImportResultDto();
        Map<String, Region> regionCache = new HashMap<>();
        Map<String, Category> categoryCache = new HashMap<>();

        regionRepository.findAll().forEach(r -> regionCache.put(r.getName().toLowerCase(), r));
        categoryRepository.findAll().forEach(c -> categoryCache.put(c.getName().toLowerCase(), c));

        Set<String> processedCodesInFile = new HashSet<>();
        List<Transaction> transactionsToSave = new ArrayList<>();

        try {
            // Stage 1: File Validation (Detect Charset/Encoding)
            CharsetAndStream encodingWrapper = detectCharsetAndStream(inputStream);
            result.setDetectedEncoding(encodingWrapper.charset.name());
            log.info("Detected file charset: {}", encodingWrapper.charset.name());

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(encodingWrapper.stream, encodingWrapper.charset))) {
                String headerLine = reader.readLine();
                if (headerLine == null) {
                    result.setStatus("Failed");
                    result.getErrors().add(CsvImportResultDto.ValidationError.builder()
                            .lineNumber(0)
                            .errorMessage("CSV file is empty")
                            .severity("ERROR")
                            .build());
                    return result;
                }

                // Auto Detect Delimiter
                String delimiter = detectDelimiter(headerLine);
                result.setDetectedDelimiter(delimiter.equals("\t") ? "TAB (\\t)" : delimiter);
                log.info("Detected column delimiter: '{}'", delimiter);

                // Stage 2: Header Validation & Column Mapping
                String[] rawHeaders = parseCsvLine(headerLine, delimiter);
                Map<String, Integer> fieldMapping = new HashMap<>();
                
                for (int i = 0; i < rawHeaders.length; i++) {
                    String standardField = resolveStandardField(rawHeaders[i]);
                    if (standardField != null) {
                        fieldMapping.put(standardField, i);
                    } else {
                        result.getIgnoredColumns().add(rawHeaders[i]);
                        result.setIgnoredColumnsCount(result.getIgnoredColumnsCount() + 1);
                    }
                }

                // Check required headers presence
                String[] requiredFields = {"transaction_code", "transaction_date", "region", "category", "amount"};
                for (String req : requiredFields) {
                    if (!fieldMapping.containsKey(req)) {
                        result.setStatus("Failed");
                        result.getErrors().add(CsvImportResultDto.ValidationError.builder()
                                .lineNumber(1)
                                .rawRow(headerLine)
                                .field(req)
                                .errorMessage("Missing required column mapping representing: " + req)
                                .suggestedFix("Ensure the CSV contains columns like '" + req + "' or a valid synonym.")
                                .severity("ERROR")
                                .build());
                        return result;
                    }
                }

                String line;
                int lineNumber = 1;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    
                    // Empty row handling (silently ignore)
                    if (line.trim().isEmpty()) {
                        result.setIgnoredRows(result.getIgnoredRows() + 1);
                        result.setSkippedRecords(result.getSkippedRecords() + 1);
                        continue;
                    }

                    result.setTotalRecords(result.getTotalRecords() + 1);
                    String[] fields = parseCsvLine(line, delimiter);

                    try {
                        // Extract and clean raw field strings (String trimming + quote removal)
                        String rawCode = getFieldValue(fields, fieldMapping, "transaction_code");
                        String rawDateStr = getFieldValue(fields, fieldMapping, "transaction_date");
                        String rawRegionName = getFieldValue(fields, fieldMapping, "region");
                        String rawCategoryName = getFieldValue(fields, fieldMapping, "category");
                        String rawAmountStr = getFieldValue(fields, fieldMapping, "amount");

                        // NULL value detection & Empty value mappings
                        String code = detectNullValue(rawCode);
                        String dateStr = detectNullValue(rawDateStr);
                        String regionName = detectNullValue(rawRegionName);
                        String categoryName = detectNullValue(rawCategoryName);
                        String amountStr = detectNullValue(rawAmountStr);

                        // Stage 3: Data Type Validation
                        if (code == null) {
                            throw new IllegalArgumentException("Transaction Code is empty or null");
                        }
                        if (dateStr == null) {
                            throw new IllegalArgumentException("Transaction Date is empty or null");
                        }
                        if (amountStr == null) {
                            throw new IllegalArgumentException("Amount is empty or null");
                        }

                        // Parse Date format
                        LocalDateTime date;
                        try {
                            date = MultiFormatDateParser.parse(dateStr);
                        } catch (Exception e) {
                            result.setParsingErrors(result.getParsingErrors() + 1);
                            throw new IllegalArgumentException("Failed to parse date '" + dateStr + "': " + e.getMessage());
                        }

                        // Parse Amount & Currency normalization
                        long amountCents;
                        try {
                            String normalizedAmount = normalizeAmountString(amountStr);
                            if (normalizedAmount.toUpperCase().contains("NAN") || normalizedAmount.toUpperCase().contains("INFINITY")) {
                                throw new IllegalArgumentException("Amount contains invalid numeric literal: '" + normalizedAmount + "'");
                            }
                            BigDecimal decimalVal = new BigDecimal(normalizedAmount);
                            
                            // Negative amount check: Returns / Refund detection
                            if (decimalVal.compareTo(BigDecimal.ZERO) < 0) {
                                String remarks = getFieldValue(fields, fieldMapping, "remarks");
                                String payment = getFieldValue(fields, fieldMapping, "payment_method");
                                String status = getFieldValue(fields, fieldMapping, "status");
                                
                                if (isRefundContext(categoryName, payment, status, remarks)) {
                                    // Allowed as negative cents
                                    amountCents = decimalVal.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
                                } else {
                                    result.setValidationErrors(result.getValidationErrors() + 1);
                                    throw new IllegalArgumentException("Negative amount is only allowed for Refunds, Returns, or Chargebacks.");
                                }
                            } else {
                                amountCents = decimalVal.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
                            }
                        } catch (NumberFormatException e) {
                            result.setParsingErrors(result.getParsingErrors() + 1);
                            throw new IllegalArgumentException("Malformed decimal format for amount: '" + amountStr + "'");
                        } catch (IllegalArgumentException e) {
                            throw e;
                        } catch (Exception e) {
                            result.setParsingErrors(result.getParsingErrors() + 1);
                            throw new IllegalArgumentException("Malformed decimal format for amount: '" + amountStr + "'");
                        }

                        // Stage 4: Business Validation (Region & Category Policies)
                        // Region policy
                        if (regionName == null || regionName.trim().isEmpty()) {
                            if ("SKIP_ROW".equalsIgnoreCase(missingRegionPolicy)) {
                                result.setValidationErrors(result.getValidationErrors() + 1);
                                throw new IllegalArgumentException("Region is missing and missingRegionPolicy is SKIP_ROW");
                            } else {
                                regionName = "Unknown";
                            }
                        }
                        String normalizedRegion = normalizeRegion(regionName);
                        String regionKey = normalizedRegion.toLowerCase();
                        Region region = regionCache.get(regionKey);
                        if (region == null) {
                            region = Region.builder().name(normalizedRegion).build();
                            region = regionRepository.save(region);
                            regionCache.put(regionKey, region);
                        }

                        // Category policy
                        if (categoryName == null || categoryName.trim().isEmpty()) {
                            if ("SKIP_ROW".equalsIgnoreCase(missingCategoryPolicy)) {
                                result.setValidationErrors(result.getValidationErrors() + 1);
                                throw new IllegalArgumentException("Category is missing and missingCategoryPolicy is SKIP_ROW");
                            } else {
                                categoryName = "Unknown";
                            }
                        }
                        String normalizedCategory = normalizeDimensionName(categoryName);
                        String categoryKey = normalizedCategory.toLowerCase();
                        Category category = categoryCache.get(categoryKey);
                        if (category == null) {
                            if ("SKIP_ROW".equalsIgnoreCase(missingCategoryPolicy)) {
                                result.setValidationErrors(result.getValidationErrors() + 1);
                                throw new IllegalArgumentException("Category '" + normalizedCategory + "' is unknown and policy is SKIP_ROW");
                            } else {
                                category = Category.builder().name(normalizedCategory).build();
                                category = categoryRepository.save(category);
                                categoryCache.put(categoryKey, category);
                            }
                        }

                        // Stage 5: Duplicate Validation & Policies
                        boolean isDuplicate = false;
                        boolean shouldUpdate = false;
                        boolean shouldReplace = false;
                        boolean shouldInsertAsNew = false;
                        String finalCode = code;

                        if (processedCodesInFile.contains(code.toLowerCase())) {
                            isDuplicate = true;
                        } else if (transactionRepository.existsByTransactionCode(code)) {
                            isDuplicate = true;
                        }

                        if (isDuplicate) {
                            if ("REJECT".equalsIgnoreCase(duplicateAction)) {
                                result.setValidationErrors(result.getValidationErrors() + 1);
                                throw new IllegalArgumentException("Duplicate transaction code detected: " + code);
                            } else if ("SKIP".equalsIgnoreCase(duplicateAction)) {
                                result.setDuplicatesSkipped(result.getDuplicatesSkipped() + 1);
                                result.setDuplicateRecords(result.getDuplicateRecords() + 1);
                                
                                result.getErrors().add(CsvImportResultDto.ValidationError.builder()
                                        .lineNumber(lineNumber)
                                        .transactionCode(code)
                                        .field("transaction_code")
                                        .originalValue(code)
                                        .errorMessage("Duplicate transaction code skipped.")
                                        .suggestedFix("Use UPDATE or REPLACE policies if you want to overwrite records.")
                                        .severity("WARNING")
                                        .rawRow(line)
                                        .build());
                                result.setWarnings(result.getWarnings() + 1);
                                continue;
                            } else if ("UPDATE".equalsIgnoreCase(duplicateAction)) {
                                shouldUpdate = true;
                            } else if ("REPLACE".equalsIgnoreCase(duplicateAction)) {
                                shouldReplace = true;
                            } else if ("INSERT_AS_NEW".equalsIgnoreCase(duplicateAction)) {
                                shouldInsertAsNew = true;
                                int suffix = 1;
                                String baseCode = code;
                                while (transactionRepository.existsByTransactionCode(baseCode + "_" + suffix) 
                                        || processedCodesInFile.contains((baseCode + "_" + suffix).toLowerCase())) {
                                    suffix++;
                                }
                                finalCode = baseCode + "_" + suffix;
                            }
                        }

                        // Resolve optional columns
                        String product = detectNullValue(getFieldValue(fields, fieldMapping, "product"));
                        Integer quantity = getOptionalFieldInt(fields, fieldMapping, "quantity");
                        Long unitPriceCents = getOptionalFieldCents(fields, fieldMapping, "unit_price");
                        String paymentMethod = detectNullValue(getFieldValue(fields, fieldMapping, "payment_method"));
                        String status = detectNullValue(getFieldValue(fields, fieldMapping, "status"));
                        String customerId = detectNullValue(getFieldValue(fields, fieldMapping, "customer_id"));
                        String employeeId = detectNullValue(getFieldValue(fields, fieldMapping, "employee_id"));
                        String storeId = detectNullValue(getFieldValue(fields, fieldMapping, "store_id"));
                        String remarks = detectNullValue(getFieldValue(fields, fieldMapping, "remarks"));

                        // If negative amount represents Refund/Return, adjust status automatically
                        if (amountCents < 0 && (status == null || status.isEmpty())) {
                            status = determineRefundStatus(categoryName, paymentMethod, status, remarks);
                        }

                        // Stage 6: Persistence
                        if (shouldUpdate || shouldReplace) {
                            Transaction existingTx = transactionRepository.findByTransactionCode(code)
                                    .orElse(null);
                            
                            if (existingTx != null) {
                                if (shouldReplace) {
                                    // REPLACE: Delete ID and save brand new
                                    transactionRepository.delete(existingTx);
                                    Transaction newTx = Transaction.builder()
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
                                    transactionRepository.save(newTx);
                                    result.setImportedRecords(result.getImportedRecords() + 1);
                                } else {
                                    // UPDATE: Overwrite fields
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
                                    result.setUpdatedRecords(result.getUpdatedRecords() + 1);
                                }
                                result.setDuplicatesUpdated(result.getDuplicatesUpdated() + 1);
                                result.setDuplicateRecords(result.getDuplicateRecords() + 1);
                            } else {
                                // Fallback if findByTransactionCode wasn't indexed correctly yet
                                shouldInsertAsNew = true;
                            }
                        }

                        if (!shouldUpdate && !shouldReplace) {
                            Transaction tx = Transaction.builder()
                                    .transactionCode(finalCode)
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
                            processedCodesInFile.add(finalCode.toLowerCase());
                            result.setImportedRecords(result.getImportedRecords() + 1);

                            // Batch writing
                            if (transactionsToSave.size() >= BATCH_SIZE) {
                                transactionRepository.saveAll(transactionsToSave);
                                transactionsToSave.clear();
                            }
                        }

                    } catch (Exception e) {
                        result.setFailedRecords(result.getFailedRecords() + 1);
                        
                        String codeVal = "";
                        try {
                            codeVal = getFieldValue(fields, fieldMapping, "transaction_code");
                        } catch (Exception ignored) {}

                        result.getErrors().add(CsvImportResultDto.ValidationError.builder()
                                .lineNumber(lineNumber)
                                .transactionCode(codeVal)
                                .field("Ingestion Pipeline")
                                .originalValue(line)
                                .errorMessage(e.getMessage())
                                .suggestedFix("Verify formatting of columns, dates, or clean duplicate/empty values.")
                                .severity("ERROR")
                                .rawRow(line)
                                .build());
                    }
                }

                // Flush remaining batch
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
                
                // Status Mapping (Never return Failed if at least 1 record saved)
                int totalIngested = result.getImportedRecords() + result.getUpdatedRecords();
                if (result.getFailedRecords() == 0 && result.getWarnings() == 0) {
                    result.setStatus("Completed Successfully");
                } else if (result.getFailedRecords() == 0 && result.getWarnings() > 0) {
                    result.setStatus("Completed With Warnings");
                } else if (totalIngested > 0) {
                    result.setStatus("Partial Success");
                } else {
                    result.setStatus("Failed");
                }

                log.info("ETL Pipeline completed. Encoding={}, Delimiter={}, Status={}, Total={}, Speed={} records/s",
                        result.getDetectedEncoding(), result.getDetectedDelimiter(), result.getStatus(), 
                        result.getTotalRecords(), result.getAverageSpeedRecordsPerSec());

                // Persist audit record in import_histories
                ImportHistory history = ImportHistory.builder()
                        .username(user.getUsername())
                        .timestamp(LocalDateTime.now())
                        .filename(user.getUsername() + "_import.csv") // Default fallback name
                        .rowsImported(result.getImportedRecords())
                        .rowsFailed(result.getFailedRecords())
                        .rowsUpdated(result.getUpdatedRecords())
                        .duplicates(result.getDuplicateRecords())
                        .processingTimeMs(processingTime)
                        .status(result.getStatus())
                        .build();
                importHistoryRepository.save(history);

                // Add to standard AuditLog
                auditLogService.logAction(
                        user.getUsername(),
                        "ETL_CSV_IMPORT",
                        String.format("ETL CSV Import: Status=%s, Imported=%d, Updated=%d, Failed=%d, Speed=%.1f rows/s",
                                result.getStatus(), result.getImportedRecords(), result.getUpdatedRecords(), 
                                result.getFailedRecords(), speed)
                );
            }
        } catch (Exception e) {
            log.error("ETL Ingestion Pipeline critically failed", e);
            result.setStatus("Failed");
            result.getErrors().add(CsvImportResultDto.ValidationError.builder()
                    .lineNumber(0)
                    .errorMessage("Critical pipeline failure: " + e.getMessage())
                    .severity("ERROR")
                    .build());
        }

        return result;
    }

    private CharsetAndStream detectCharsetAndStream(InputStream rawStream) throws Exception {
        byte[] bom = new byte[4];
        PushbackInputStream pushbackStream = new PushbackInputStream(rawStream, 4);
        int n = pushbackStream.read(bom, 0, 4);
        if (n <= 0) {
            return new CharsetAndStream(java.nio.charset.StandardCharsets.UTF_8, pushbackStream);
        }

        int unread = n;
        Charset charset = java.nio.charset.StandardCharsets.UTF_8;

        if ((bom[0] == (byte) 0xEF) && (bom[1] == (byte) 0xBB) && (bom[2] == (byte) 0xBF)) {
            charset = java.nio.charset.StandardCharsets.UTF_8;
            unread = n - 3;
        } else if ((bom[0] == (byte) 0xFE) && (bom[1] == (byte) 0xFF)) {
            charset = java.nio.charset.StandardCharsets.UTF_16BE;
            unread = n - 2;
        } else if ((bom[0] == (byte) 0xFF) && (bom[1] == (byte) 0xFE)) {
            charset = java.nio.charset.StandardCharsets.UTF_16LE;
            unread = n - 2;
        }

        if (unread > 0) {
            pushbackStream.unread(bom, n - unread, unread);
        }

        return new CharsetAndStream(charset, pushbackStream);
    }

    private String detectDelimiter(String headerLine) {
        if (headerLine == null || headerLine.isEmpty()) {
            return ",";
        }
        int comma = countOccurrences(headerLine, ',');
        int semicolon = countOccurrences(headerLine, ';');
        int tab = countOccurrences(headerLine, '\t');
        int pipe = countOccurrences(headerLine, '|');

        if (semicolon > comma && semicolon > tab && semicolon > pipe) return ";";
        if (tab > comma && tab > semicolon && tab > pipe) return "\t";
        if (pipe > comma && pipe > semicolon && pipe > tab) return "|";
        return ",";
    }

    private int countOccurrences(String str, char ch) {
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == ch) count++;
        }
        return count;
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

    private String[] parseCsvLine(String line, String delimiter) {
        if (line == null) return new String[0];
        
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        char delimChar = delimiter.charAt(0);

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimChar && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());

        String[] result = new String[tokens.size()];
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i).trim();
            if (token.startsWith("\"") && token.endsWith("\"")) {
                token = token.substring(1, token.length() - 1).trim();
            } else if (token.startsWith("'") && token.endsWith("'")) {
                token = token.substring(1, token.length() - 1).trim();
            }
            // Remove zero-width spaces or hidden Unicode chars
            token = token.replaceAll("[\\p{Cf}\\u200B\\u200D\\uFEFF]", "");
            result[i] = token;
        }
        return result;
    }

    private String getFieldValue(String[] fields, Map<String, Integer> mapping, String key) {
        Integer index = mapping.get(key);
        if (index == null || index >= fields.length) {
            return "";
        }
        return fields[index];
    }

    private String detectNullValue(String val) {
        if (val == null) return null;
        String clean = val.trim();
        String upper = clean.toUpperCase();
        if (upper.isEmpty() || 
            upper.equals("NULL") || 
            upper.equals("N/A") || 
            upper.equals("NAN") || 
            upper.equals("NONE") || 
            upper.equals("-") ||
            upper.equals("UNDEFINED")) {
            return null;
        }
        return clean;
    }

    private String normalizeAmountString(String amountStr) {
        if (amountStr == null) return "";
        String clean = amountStr.trim();
        
        // Remove currency symbols (e.g. ₹, $, €, £, etc.)
        clean = clean.replaceAll("[₹\\$\\u20AC\\u00A3\\u00A5\\u20BD\\u20A8\\s]", "");
        
        if (clean.matches(".*\\d+[,|\\.]\\d{2}$")) {
            char separator = clean.charAt(clean.length() - 3);
            if (separator == ',') {
                clean = clean.replace(".", "").replace(",", ".");
            } else if (separator == '.') {
                clean = clean.replace(",", "");
            }
        } else {
            clean = clean.replace(",", "");
        }
        
        return clean.trim();
    }

    private String normalizeRegion(String regionStr) {
        if (regionStr == null) return "";
        String clean = regionStr.trim().toLowerCase();
        
        // Strip common suffix expressions like "region" or "zone"
        clean = clean.replaceAll("\\b(region|zone)\\b", "").trim();
        
        return normalizeDimensionName(clean);
    }

    private String normalizeDimensionName(String name) {
        if (name == null) return "";
        String clean = name.trim();
        if (clean.isEmpty()) return "";
        
        String[] words = clean.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0)));
            if (w.length() > 1) {
                sb.append(w.substring(1).toLowerCase());
            }
            sb.append(" ");
        }
        return sb.toString().trim();
    }

    private Integer getOptionalFieldInt(String[] fields, Map<String, Integer> mapping, String key) {
        String val = detectNullValue(getFieldValue(fields, mapping, key));
        if (val == null) return null;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long getOptionalFieldCents(String[] fields, Map<String, Integer> mapping, String key) {
        String val = detectNullValue(getFieldValue(fields, mapping, key));
        if (val == null) return null;
        try {
            String norm = normalizeAmountString(val);
            BigDecimal dec = new BigDecimal(norm);
            return dec.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).longValue();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isRefundContext(String category, String payment, String status, String remarks) {
        String fullText = (safeString(category) + " " + safeString(payment) + " " + safeString(status) + " " + safeString(remarks)).toLowerCase();
        return fullText.contains("refund") || fullText.contains("return") || fullText.contains("chargeback");
    }

    private String determineRefundStatus(String category, String payment, String status, String remarks) {
        String fullText = (safeString(category) + " " + safeString(payment) + " " + safeString(status) + " " + safeString(remarks)).toLowerCase();
        if (fullText.contains("chargeback")) return "CHARGEBACK";
        if (fullText.contains("refund")) return "REFUND";
        return "RETURN";
    }

    private String safeString(String str) {
        return str == null ? "" : str;
    }

    private double roundDouble(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static class CharsetAndStream {
        final Charset charset;
        final InputStream stream;
        CharsetAndStream(Charset charset, InputStream stream) {
            this.charset = charset;
            this.stream = stream;
        }
    }
}
