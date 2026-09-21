package com.sudo0x.simple.identity.permission.repository;

import com.sudo0x.simple.identity.permission.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    boolean existsByCode(String code);

    Optional<Permission> findByCode(String code);
}
