package com.salessphere.backend.controller;

import com.salessphere.backend.dto.AggregationResultDto;
import com.salessphere.backend.service.DashboardService;
import com.salessphere.backend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<AggregationResultDto> getReportSummary() {
        // Reuses dashboard data structured representation
        return ResponseEntity.ok(dashboardService.getDashboardData());
    }

    @GetMapping("/regional/csv")
    public ResponseEntity<byte[]> downloadRegionalReport() {
        String csvData = reportService.generateRegionalReportCsv();
        byte[] bytes = csvData.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=regional_sales_report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    @GetMapping("/category/csv")
    public ResponseEntity<byte[]> downloadCategoryReport() {
        String csvData = reportService.generateCategoryReportCsv();
        byte[] bytes = csvData.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=category_sales_report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }

    @GetMapping("/executive/csv")
    public ResponseEntity<byte[]> downloadExecutiveSummary() {
        String csvData = reportService.generateExecutiveSummaryCsv();
        byte[] bytes = csvData.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=executive_summary_report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(bytes);
    }
}
