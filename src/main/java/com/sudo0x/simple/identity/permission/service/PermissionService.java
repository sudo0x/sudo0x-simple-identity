package com.sudo0x.simple.identity.permission.service;

import com.sudo0x.simple.identity.common.exception.PermissionNotFoundException;
import com.sudo0x.simple.identity.permission.dto.PermissionResponse;
import com.sudo0x.simple.identity.permission.mapper.PermissionMapper;
import com.sudo0x.simple.identity.permission.repository.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    @Transactional(readOnly = true)
    public List<PermissionResponse> findAll() {
        return permissionRepository.findAll().stream()
                .map(permissionMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PermissionResponse findById(UUID id) {
        return permissionRepository.findById(id)
                .map(permissionMapper::toResponse)
                .orElseThrow(() -> new PermissionNotFoundException(id));
    }
}
