package com.sudo0x.simple.identity.role.repository;

import com.sudo0x.simple.identity.role.entity.Role;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);

    Optional<Role> findByName(String name);

    @EntityGraph(attributePaths = "permissions")
    @Query("SELECT r FROM Role r WHERE r.id = :id")
    Optional<Role> findByIdWithPermissions(@Param("id") UUID id);

    @EntityGraph(attributePaths = "permissions")
    @Query("SELECT r FROM Role r")
    List<Role> findAllWithPermissions();

    @Query("SELECT COUNT(u) FROM User u JOIN u.roles r WHERE r.id = :roleId")
    long countUsersWithRole(@Param("roleId") UUID roleId);
}
