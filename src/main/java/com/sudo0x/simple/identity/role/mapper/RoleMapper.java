package com.sudo0x.simple.identity.role.mapper;

import com.sudo0x.simple.identity.permission.entity.Permission;
import com.sudo0x.simple.identity.role.dto.RoleResponse;
import com.sudo0x.simple.identity.role.entity.Role;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class RoleMapper {

    public RoleResponse toResponse(Role role) {
        return new RoleResponse(
                role.getId(),
                role.getName(),
                role.getDescription(),
                role.getPermissions().stream()
                        .map(Permission::getCode)
                        .collect(Collectors.toSet()),
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }
}
