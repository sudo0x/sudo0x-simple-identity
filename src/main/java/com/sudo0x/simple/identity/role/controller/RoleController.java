package com.sudo0x.simple.identity.role.controller;

import com.sudo0x.simple.identity.common.response.ApiResponse;
import com.sudo0x.simple.identity.role.dto.CreateRoleRequest;
import com.sudo0x.simple.identity.role.dto.RoleResponse;
import com.sudo0x.simple.identity.role.dto.UpdateRoleRequest;
import com.sudo0x.simple.identity.role.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("hasAuthority('permission:role:read')")
    @Operation(summary = "List all roles")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(roleService.findAll()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('permission:role:read')")
    @Operation(summary = "Get role by ID")
    public ResponseEntity<ApiResponse<RoleResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('permission:role:create')")
    @Operation(summary = "Create a new role")
    public ResponseEntity<ApiResponse<RoleResponse>> create(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(roleService.create(request)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('permission:role:update')")
    @Operation(summary = "Update a role")
    public ResponseEntity<ApiResponse<RoleResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('permission:role:delete')")
    @Operation(summary = "Delete a role")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        roleService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Role deleted"));
    }

    @PutMapping("/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('permission:role:update')")
    @Operation(summary = "Add a permission to a role")
    public ResponseEntity<ApiResponse<RoleResponse>> addPermission(
            @PathVariable UUID roleId, @PathVariable UUID permissionId) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.addPermission(roleId, permissionId)));
    }

    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    @PreAuthorize("hasAuthority('permission:role:update')")
    @Operation(summary = "Remove a permission from a role")
    public ResponseEntity<ApiResponse<RoleResponse>> removePermission(
            @PathVariable UUID roleId, @PathVariable UUID permissionId) {
        return ResponseEntity.ok(ApiResponse.ok(roleService.removePermission(roleId, permissionId)));
    }
}
