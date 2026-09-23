package com.leadquote.service;

import com.leadquote.entity.AuditLog;
import com.leadquote.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void log(String entityType, Long entityId, String event, String details) {
        String performedBy;
        try {
            performedBy = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName()
                    : "SYSTEM";
        } catch (Exception e) {
            performedBy = "SYSTEM";
        }
        auditLogRepository.save(AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .event(event)
                .details(details)
                .performedBy(performedBy)
                .build());
    }
}
