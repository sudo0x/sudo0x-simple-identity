package com.sudo0x.simple.identity.user;

import com.sudo0x.simple.identity.audit.service.AuditService;
import com.sudo0x.simple.identity.common.exception.EmailAlreadyExistsException;
import com.sudo0x.simple.identity.common.exception.UserNotFoundException;
import com.sudo0x.simple.identity.common.exception.UsernameAlreadyExistsException;
import com.sudo0x.simple.identity.credential.service.CredentialService;
import com.sudo0x.simple.identity.role.repository.RoleRepository;
import com.sudo0x.simple.identity.user.dto.CreateUserRequest;
import com.sudo0x.simple.identity.user.dto.UpdateUserRequest;
import com.sudo0x.simple.identity.user.dto.UserResponse;
import com.sudo0x.simple.identity.user.entity.User;
import com.sudo0x.simple.identity.user.entity.UserStatus;
import com.sudo0x.simple.identity.user.mapper.UserMapper;
import com.sudo0x.simple.identity.user.repository.UserRepository;
import com.sudo0x.simple.identity.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock CredentialService credentialService;
    @Mock UserMapper userMapper;
    @Mock AuditService auditService;

    @InjectMocks UserService userService;

    @Test
    void createUserSuccessfully() {
        CreateUserRequest request = new CreateUserRequest("newuser", "new@test.com", "Password1");
        UUID userId = UUID.randomUUID();
        User savedUser = new User("newuser", "new@test.com");

        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(userRepository.save(any())).thenReturn(savedUser);
        when(userMapper.toResponse(savedUser)).thenReturn(
                new UserResponse(userId, "newuser", "new@test.com", "ACTIVE", false, Set.of(), null, null));

        UserResponse response = userService.create(request, UUID.randomUUID());

        assertThat(response.username()).isEqualTo("newuser");
        verify(credentialService).createCredential(any(), eq("Password1"));
    }

    @Test
    void createUserWithDuplicateUsernameThrows() {
        when(userRepository.existsByUsername("existinguser")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(
                new CreateUserRequest("existinguser", "other@test.com", "Password1"),
                UUID.randomUUID()))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void createUserWithDuplicateEmailThrows() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("existing@test.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(
                new CreateUserRequest("newuser", "existing@test.com", "Password1"),
                UUID.randomUUID()))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    void findByIdNotFoundThrows() {
        UUID id = UUID.randomUUID();
        when(userRepository.findByIdWithRolesAndPermissions(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(id))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void deleteUserSetsStatusToDeleted() {
        UUID id = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        User user = new User("user", "user@test.com");

        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        userService.delete(id, actorId);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        verify(userRepository).save(user);
    }

    @Test
    void deleteAlreadyDeletedUserThrows() {
        UUID id = UUID.randomUUID();
        User deletedUser = new User("user", "user@test.com");
        deletedUser.setStatus(UserStatus.DELETED);

        when(userRepository.findById(id)).thenReturn(Optional.of(deletedUser));

        assertThatThrownBy(() -> userService.delete(id, UUID.randomUUID()))
                .isInstanceOf(UserNotFoundException.class);
    }
}
