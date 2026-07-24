package com.schoolcanopy.audit;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AuditService {

    @Inject
    AuditLogRepository auditLogRepository;

    @Transactional
    public void log(String actionType, UUID schoolId, String targetEntityId, UUID performedBy, String details) {
        AuditLog entry = new AuditLog();
        entry.setActionType(actionType);
        entry.setSchoolId(schoolId);
        entry.setTargetEntityId(targetEntityId);
        entry.setPerformedBy(performedBy);
        entry.setDetails(details);
        entry.setCreatedAt(LocalDateTime.now());
        auditLogRepository.persist(entry);
    }
}
