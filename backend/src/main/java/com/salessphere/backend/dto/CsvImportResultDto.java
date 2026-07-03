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
    private int importedRecords;
    private int duplicateRecords;
    private int failedRecords;
    private int skippedRecords;
    private int ignoredColumnsCount;
    @Builder.Default
    private List<String> ignoredColumns = new ArrayList<>();
    private long processingTimeMs;
    private double averageSpeedRecordsPerSec;
    private String status; // SUCCESS, PARTIAL_SUCCESS, FAILED
    @Builder.Default
    private List<ValidationError> errors = new ArrayList<>();

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ValidationError {
        private int lineNumber;
        private String rawRow;
        private String errorMessage;
    }
}
