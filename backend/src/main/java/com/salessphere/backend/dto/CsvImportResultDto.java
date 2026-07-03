package com.salessphere.backend.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CsvImportResultDto {
    private int totalRecords;
    private int importedRecords;      // Successfully Imported / Saved
    private int updatedRecords;       // Successfully Updated / Saved
    private int duplicatesSkipped;    // Duplicate (Skipped)
    private int duplicatesUpdated;    // Duplicate (Updated)
    private int validationErrors;     // Validation Error count
    private int parsingErrors;        // Parsing Error count
    private int warnings;             // Warning count
    private int ignoredRows;          // Ignored (e.g. empty or metadata rows)
    
    private int duplicateRecords;     // Legacy support
    private int failedRecords;        // Legacy support
    private int skippedRecords;       // Legacy support
    
    private int ignoredColumnsCount;
    @Builder.Default
    private List<String> ignoredColumns = new ArrayList<>();
    
    private long processingTimeMs;
    private double averageSpeedRecordsPerSec;
    private String status;            // COMPLETED_SUCCESSFULLY, COMPLETED_WITH_WARNINGS, PARTIAL_SUCCESS, FAILED
    private String detectedEncoding;
    private String detectedDelimiter;
    
    @Builder.Default
    private List<ValidationError> errors = new ArrayList<>();

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ValidationError {
        private int lineNumber;
        private String transactionCode;
        private String field;
        private String originalValue;
        private String errorMessage;
        private String suggestedFix;
        private String severity;       // ERROR, WARNING
        private String rawRow;
    }
}
