package com.sudo0x.simple.identity.permission.controller;

import com.sudo0x.simple.identity.common.response.ApiResponse;
import com.sudo0x.simple.identity.permission.dto.PermissionResponse;
import com.sudo0x.simple.identity.permission.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@Tag(name = "Permission Management")
@SecurityRequirement(name = "bearerAuth")
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    @PreAuthorize("hasAuthority('permission:permission:read')")
    @Operation(summary = "List all permissions")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(permissionService.findAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('permission:permission:read')")
    @Operation(summary = "Get a permission by ID")
    public ResponseEntity<ApiResponse<PermissionResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(permissionService.findById(id)));
    }
}
