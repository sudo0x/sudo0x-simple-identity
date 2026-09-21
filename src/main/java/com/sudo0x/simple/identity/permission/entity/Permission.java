package com.sudo0x.simple.identity.permission.entity;

import com.sudo0x.simple.identity.common.audit.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
public class Permission extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 128)
    private String code;

    @Column(length = 255)
    private String description;

    public Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }
}
