package com.sudo0x.simple.identity.user.service;

import com.sudo0x.simple.identity.audit.entity.AuditEventType;
import com.sudo0x.simple.identity.audit.service.AuditService;
import com.sudo0x.simple.identity.common.exception.*;
import com.sudo0x.simple.identity.credential.service.CredentialService;
import com.sudo0x.simple.identity.role.entity.Role;
import com.sudo0x.simple.identity.role.repository.RoleRepository;
import com.sudo0x.simple.identity.user.dto.CreateUserRequest;
import com.sudo0x.simple.identity.user.dto.UpdateUserRequest;
import com.sudo0x.simple.identity.user.dto.UserResponse;
import com.sudo0x.simple.identity.user.entity.User;
import com.sudo0x.simple.identity.user.entity.UserStatus;
import com.sudo0x.simple.identity.user.mapper.UserMapper;
import com.sudo0x.simple.identity.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CredentialService credentialService;
    private final UserMapper userMapper;
    private final AuditService auditService;

    @Transactional
    public UserResponse create(CreateUserRequest request, UUID actorId) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UsernameAlreadyExistsException(request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = new User(request.username(), request.email().toLowerCase());
        user = userRepository.save(user);

        credentialService.createCredential(user.getId(), request.password());

        auditService.record(actorId, AuditEventType.USER_CREATED,
                "Created user: " + user.getUsername(), null, null);
        log.info("User created: id={} username={}", user.getId(), user.getUsername());

        return userMapper.toResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> search(String username, String email, UserStatus status, Pageable pageable) {
        return userRepository.search(username, email, status, java.util.Set.of(UserStatus.DELETED), pageable)
                .map(userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        User user = userRepository.findByIdWithRolesAndPermissions(id)
                .filter(u -> u.getStatus() != UserStatus.DELETED)
                .orElseThrow(() -> new UserNotFoundException(id));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, UUID actorId) {
        User user = userRepository.findById(id)
                .filter(u -> u.getStatus() != UserStatus.DELETED)
                .orElseThrow(() -> new UserNotFoundException(id));

        if (StringUtils.hasText(request.username())) {
            if (userRepository.existsByUsernameAndIdNot(request.username(), id)) {
                throw new UsernameAlreadyExistsException(request.username());
            }
            user.setUsername(request.username());
        }
        if (StringUtils.hasText(request.email())) {
            if (userRepository.existsByEmailAndIdNot(request.email().toLowerCase(), id)) {
                throw new EmailAlreadyExistsException(request.email());
            }
            user.setEmail(request.email().toLowerCase());
        }
        if (StringUtils.hasText(request.status())) {
            try {
                UserStatus newStatus = UserStatus.valueOf(request.status().toUpperCase());
                if (newStatus == UserStatus.DELETED) {
                    throw new IllegalArgumentException("Use DELETE endpoint to remove a user");
                }
                if (newStatus == UserStatus.INACTIVE && user.getStatus() != UserStatus.INACTIVE) {
                    auditService.record(actorId, AuditEventType.USER_DISABLED,
                            "Disabled user: " + user.getUsername(), null, null);
                }
                user.setStatus(newStatus);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status: " + request.status());
            }
        }

        user = userRepository.save(user);
        auditService.record(actorId, AuditEventType.USER_UPDATED,
                "Updated user: " + user.getUsername(), null, null);
        return userMapper.toResponse(user);
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        User user = userRepository.findById(id)
                .filter(u -> u.getStatus() != UserStatus.DELETED)
                .orElseThrow(() -> new UserNotFoundException(id));

        // Soft delete
        user.setStatus(UserStatus.DELETED);
        userRepository.save(user);
        auditService.record(actorId, AuditEventType.USER_DELETED,
                "Deleted user: " + user.getUsername(), null, null);
        log.info("User soft-deleted: id={}", id);
    }

    @Transactional
    public UserResponse assignRole(UUID userId, UUID roleId, UUID actorId) {
        User user = userRepository.findByIdWithRolesAndPermissions(userId)
                .filter(u -> u.getStatus() != UserStatus.DELETED)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        user.getRoles().add(role);
        user = userRepository.save(user);
        auditService.record(actorId, AuditEventType.ROLE_ASSIGNED,
                "Assigned role " + role.getName() + " to user " + user.getUsername(), null, null);
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse removeRole(UUID userId, UUID roleId, UUID actorId) {
        User user = userRepository.findByIdWithRolesAndPermissions(userId)
                .filter(u -> u.getStatus() != UserStatus.DELETED)
                .orElseThrow(() -> new UserNotFoundException(userId));
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        user.getRoles().remove(role);
        user = userRepository.save(user);
        auditService.record(actorId, AuditEventType.ROLE_REMOVED,
                "Removed role " + role.getName() + " from user " + user.getUsername(), null, null);
        return userMapper.toResponse(user);
    }
}
