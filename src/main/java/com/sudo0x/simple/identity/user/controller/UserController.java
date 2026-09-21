package com.sudo0x.simple.identity.user.controller;

import com.sudo0x.simple.identity.common.response.ApiResponse;
import com.sudo0x.simple.identity.common.response.PagedResponse;
import com.sudo0x.simple.identity.common.security.CurrentUser;
import com.sudo0x.simple.identity.common.security.SecurityPrincipal;
import com.sudo0x.simple.identity.user.dto.CreateUserRequest;
import com.sudo0x.simple.identity.user.dto.UpdateUserRequest;
import com.sudo0x.simple.identity.user.dto.UserResponse;
import com.sudo0x.simple.identity.user.entity.UserStatus;
import com.sudo0x.simple.identity.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Administrative user CRUD operations")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('permission:user:read')")
    @Operation(summary = "List and search users with pagination")
    public ResponseEntity<ApiResponse<PagedResponse<UserResponse>>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        PageRequest pageable = PageRequest.of(page, size, sort);

        Page<UserResponse> result = userService.search(username, email, status, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PagedResponse.from(result)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('permission:user:read')")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('permission:user:create')")
    @Operation(summary = "Create a new user")
    public ResponseEntity<ApiResponse<UserResponse>> create(
            @Valid @RequestBody CreateUserRequest request,
            @CurrentUser SecurityPrincipal principal) {
        UserResponse created = userService.create(request, principal.userId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('permission:user:update')")
    @Operation(summary = "Update a user")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request,
            @CurrentUser SecurityPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(userService.update(id, request, principal.userId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('permission:user:delete')")
    @Operation(summary = "Delete (soft) a user")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            @CurrentUser SecurityPrincipal principal) {
        userService.delete(id, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null, "User deleted"));
    }

    @PutMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('permission:role:update')")
    @Operation(summary = "Assign a role to a user")
    public ResponseEntity<ApiResponse<UserResponse>> assignRole(
            @PathVariable UUID userId,
            @PathVariable UUID roleId,
            @CurrentUser SecurityPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(userService.assignRole(userId, roleId, principal.userId())));
    }

    @DeleteMapping("/{userId}/roles/{roleId}")
    @PreAuthorize("hasAuthority('permission:role:update')")
    @Operation(summary = "Remove a role from a user")
    public ResponseEntity<ApiResponse<UserResponse>> removeRole(
            @PathVariable UUID userId,
            @PathVariable UUID roleId,
            @CurrentUser SecurityPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(userService.removeRole(userId, roleId, principal.userId())));
    }
}
