package com.sudo0x.simple.identity.credential.repository;

import com.sudo0x.simple.identity.credential.entity.Credential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    Optional<Credential> findByUserId(UUID userId);
}
