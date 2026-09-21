package com.sudo0x.simple.identity.audit.service;

import com.sudo0x.simple.identity.audit.entity.AuditEvent;
import com.sudo0x.simple.identity.audit.entity.AuditEventType;
import com.sudo0x.simple.identity.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Records an audit event asynchronously so it never blocks the main request.
     * Uses REQUIRES_NEW so audit persistence is independent of the caller's transaction.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, AuditEventType type, String details,
                       String ipAddress, String userAgent) {
        try {
            auditEventRepository.save(new AuditEvent(userId, type, details, ipAddress, userAgent));
        } catch (Exception e) {
            // Audit failure must never break the primary operation
            log.error("Failed to record audit event type={} userId={}", type, userId, e);
        }
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditEventType type, String details, String ipAddress, String userAgent) {
        record(null, type, details, ipAddress, userAgent);
    }
}
