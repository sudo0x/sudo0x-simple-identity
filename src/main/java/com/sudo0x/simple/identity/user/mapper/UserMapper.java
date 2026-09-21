package com.sudo0x.simple.identity.user.mapper;

import com.sudo0x.simple.identity.role.entity.Role;
import com.sudo0x.simple.identity.user.dto.UserResponse;
import com.sudo0x.simple.identity.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getStatus().name(),
                user.isEmailVerified(),
                roleNames,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
