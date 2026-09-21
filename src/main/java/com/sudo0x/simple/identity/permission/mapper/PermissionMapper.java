package com.sudo0x.simple.identity.permission.mapper;

import com.sudo0x.simple.identity.permission.dto.PermissionResponse;
import com.sudo0x.simple.identity.permission.entity.Permission;
import org.springframework.stereotype.Component;

@Component
public class PermissionMapper {

    public PermissionResponse toResponse(Permission permission) {
        return new PermissionResponse(
                permission.getId(),
                permission.getCode(),
                permission.getDescription(),
                permission.getCreatedAt()
        );
    }
}
