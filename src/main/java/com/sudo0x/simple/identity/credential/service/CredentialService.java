package com.sudo0x.simple.identity.credential.service;

import com.sudo0x.simple.identity.common.config.AppProperties;
import com.sudo0x.simple.identity.common.exception.PasswordPolicyException;
import com.sudo0x.simple.identity.credential.entity.Credential;
import com.sudo0x.simple.identity.credential.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CredentialService {

    private final CredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;

    @Transactional
    public void createCredential(UUID userId, String rawPassword) {
        validatePasswordPolicy(rawPassword);
        String hash = passwordEncoder.encode(rawPassword);
        credentialRepository.save(new Credential(userId, hash));
    }

    @Transactional
    public void updatePassword(UUID userId, String rawPassword) {
        validatePasswordPolicy(rawPassword);
        Credential credential = credentialRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("Credential not found for user: " + userId));
        credential.setPasswordHash(passwordEncoder.encode(rawPassword));
        credentialRepository.save(credential);
    }

    @Transactional(readOnly = true)
    public boolean verify(UUID userId, String rawPassword) {
        return credentialRepository.findByUserId(userId)
                .map(c -> passwordEncoder.matches(rawPassword, c.getPasswordHash()))
                .orElse(false);
    }

    public void validatePasswordPolicy(String password) {
        AppProperties.PasswordProperties p = props.password();
        List<String> violations = new ArrayList<>();

        if (password == null || password.length() < p.minimumLength()) {
            violations.add("Password must be at least " + p.minimumLength() + " characters");
        }
        if (p.requireUppercase() && (password == null || password.chars().noneMatch(Character::isUpperCase))) {
            violations.add("Password must contain at least one uppercase letter");
        }
        if (p.requireLowercase() && (password == null || password.chars().noneMatch(Character::isLowerCase))) {
            violations.add("Password must contain at least one lowercase letter");
        }
        if (p.requireDigit() && (password == null || password.chars().noneMatch(Character::isDigit))) {
            violations.add("Password must contain at least one digit");
        }
        if (p.requireSpecial() && (password == null || !password.matches(".*[!@#$%^&*(),.?\":{}|<>].*"))) {
            violations.add("Password must contain at least one special character");
        }

        if (!violations.isEmpty()) {
            throw new PasswordPolicyException(String.join("; ", violations));
        }
    }
}
