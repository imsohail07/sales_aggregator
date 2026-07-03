package com.salessphere.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "import_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ImportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private String filename;

    @Column(name = "rows_imported", nullable = false)
    private int rowsImported;

    @Column(name = "rows_failed", nullable = false)
    private int rowsFailed;

    @Column(name = "rows_updated", nullable = false)
    private int rowsUpdated;

    @Column(name = "duplicates", nullable = false)
    private int duplicates;

    @Column(name = "processing_time_ms", nullable = false)
    private long processingTimeMs;

    @Column(nullable = false)
    private String status; // COMPLETED_SUCCESSFULLY, COMPLETED_WITH_WARNINGS, PARTIAL_SUCCESS, FAILED
}
