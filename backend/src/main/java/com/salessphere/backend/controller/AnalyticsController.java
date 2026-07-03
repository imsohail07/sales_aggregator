package com.salessphere.backend.controller;

import com.salessphere.backend.dto.AnalyticsDto;
import com.salessphere.backend.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    public ResponseEntity<AnalyticsDto> getAnalytics() {
        AnalyticsDto analyticsData = analyticsService.getAnalyticsData();
        return ResponseEntity.ok(analyticsData);
    }
}
