package com.sudo0x.simple.identity.password.repository;

import com.sudo0x.simple.identity.password.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.userId = :userId AND t.usedAt IS NULL")
    int invalidateAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") Instant before);
}
