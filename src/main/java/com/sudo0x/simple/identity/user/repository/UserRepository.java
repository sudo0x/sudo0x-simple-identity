package com.sudo0x.simple.identity.user.repository;

import com.sudo0x.simple.identity.user.entity.User;
import com.sudo0x.simple.identity.user.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsernameAndIdNot(String username, UUID id);

    boolean existsByEmailAndIdNot(String email, UUID id);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsernameWithRolesAndPermissions(@Param("username") String username);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdWithRolesAndPermissions(@Param("id") UUID id);

    @Query("""
            SELECT u FROM User u
            WHERE (:username IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :username, '%')))
              AND (:email    IS NULL OR LOWER(u.email)    LIKE LOWER(CONCAT('%', :email,    '%')))
              AND (:status   IS NULL OR u.status = :status)
              AND u.status NOT IN :excludedStatuses
            """)
    Page<User> search(
            @Param("username") String username,
            @Param("email") String email,
            @Param("status") UserStatus status,
            @Param("excludedStatuses") java.util.Set<UserStatus> excludedStatuses,
            Pageable pageable
    );
}
