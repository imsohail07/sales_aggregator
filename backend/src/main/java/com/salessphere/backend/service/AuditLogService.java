package com.salessphere.backend.service;

import com.salessphere.backend.entity.AuditLog;
import com.salessphere.backend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Persists an audit event to the database.
     * Uses Propagation.REQUIRES_NEW to ensure the log is committed even if the outer transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(String username, String action, String details) {
        log.info("Audit Action: User={} | Action={} | Details={}", username, action, details);
        try {
            AuditLog auditLog = AuditLog.builder()
                    .username(username)
                    .action(action)
                    .details(details)
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to persist audit log", e);
        }
    }
}
