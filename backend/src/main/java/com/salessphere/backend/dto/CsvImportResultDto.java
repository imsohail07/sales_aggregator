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
