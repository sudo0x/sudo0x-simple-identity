package com.sudo0x.simple.identity.audit.repository;

import com.sudo0x.simple.identity.audit.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
}
