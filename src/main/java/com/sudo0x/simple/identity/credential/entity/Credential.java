package com.sudo0x.simple.identity.credential.entity;

import com.sudo0x.simple.identity.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "credentials")
@Getter
@Setter
@NoArgsConstructor
public class Credential extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    public Credential(UUID userId, String passwordHash) {
        this.userId = userId;
        this.passwordHash = passwordHash;
    }
}
