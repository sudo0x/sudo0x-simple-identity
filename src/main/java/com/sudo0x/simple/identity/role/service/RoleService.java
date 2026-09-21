package com.sudo0x.simple.identity.role.service;

import com.sudo0x.simple.identity.common.exception.RoleNotFoundException;
import com.sudo0x.simple.identity.permission.entity.Permission;
import com.sudo0x.simple.identity.permission.repository.PermissionRepository;
import com.sudo0x.simple.identity.role.dto.CreateRoleRequest;
import com.sudo0x.simple.identity.role.dto.RoleResponse;
import com.sudo0x.simple.identity.role.dto.UpdateRoleRequest;
import com.sudo0x.simple.identity.role.entity.Role;
import com.sudo0x.simple.identity.role.mapper.RoleMapper;
import com.sudo0x.simple.identity.role.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleMapper roleMapper;

    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        return roleRepository.findAllWithPermissions().stream()
                .map(roleMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse findById(UUID id) {
        Role role = roleRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new RoleNotFoundException(id));
        return roleMapper.toResponse(role);
    }

    @Transactional
    public RoleResponse create(CreateRoleRequest request) {
        if (roleRepository.existsByName(request.name())) {
            throw new IllegalArgumentException("Role already exists: " + request.name());
        }
        Role role = new Role(request.name(), request.description());
        return roleMapper.toResponse(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse update(UUID id, UpdateRoleRequest request) {
        Role role = roleRepository.findByIdWithPermissions(id)
                .orElseThrow(() -> new RoleNotFoundException(id));

        if (StringUtils.hasText(request.name())) {
            if (roleRepository.existsByNameAndIdNot(request.name(), id)) {
                throw new IllegalArgumentException("Role name already in use: " + request.name());
            }
            role.setName(request.name());
        }
        if (request.description() != null) {
            role.setDescription(request.description());
        }

        return roleMapper.toResponse(roleRepository.save(role));
    }

    @Transactional
    public void delete(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RoleNotFoundException(id));

        long usersWithRole = roleRepository.countUsersWithRole(id);
        if (usersWithRole > 0) {
            throw new IllegalStateException(
                    "Cannot delete role '" + role.getName() + "': it is still assigned to " + usersWithRole + " user(s)");
        }

        roleRepository.delete(role);
    }

    @Transactional
    public RoleResponse addPermission(UUID roleId, UUID permissionId) {
        Role role = roleRepository.findByIdWithPermissions(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new com.sudo0x.simple.identity.common.exception.PermissionNotFoundException(permissionId));

        role.getPermissions().add(permission);
        return roleMapper.toResponse(roleRepository.save(role));
    }

    @Transactional
    public RoleResponse removePermission(UUID roleId, UUID permissionId) {
        Role role = roleRepository.findByIdWithPermissions(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new com.sudo0x.simple.identity.common.exception.PermissionNotFoundException(permissionId));

        role.getPermissions().remove(permission);
        return roleMapper.toResponse(roleRepository.save(role));
    }
}
