package com.sudo0x.simple.identity.common.config;

import com.sudo0x.simple.identity.credential.repository.CredentialRepository;
import com.sudo0x.simple.identity.credential.service.CredentialService;
import com.sudo0x.simple.identity.role.repository.RoleRepository;
import com.sudo0x.simple.identity.user.entity.User;
import com.sudo0x.simple.identity.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Development seed data initializer.
 * Only runs when identity.seed.enabled=true.
 * NEVER enabled in production — set SEED_ENABLED=false (the default).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeedDataInitializer implements ApplicationRunner {

    private final AppProperties props;
    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final CredentialService credentialService;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled()) {
            return;
        }

        String adminUsername = props.seed().adminUsername();
        String adminEmail    = props.seed().adminEmail();
        String adminPassword = props.seed().adminPassword();

        if (!StringUtils.hasText(adminPassword)) {
            log.warn("Seed is enabled but SEED_ADMIN_PASSWORD is not set. Skipping admin user creation.");
            return;
        }

        if (userRepository.existsByUsername(adminUsername)) {
            log.info("Seed: admin user '{}' already exists, skipping.", adminUsername);
            return;
        }

        User admin = new User(adminUsername, adminEmail.toLowerCase());
        admin.setEmailVerified(true);
        final User savedAdmin = userRepository.save(admin);

        credentialService.createCredential(savedAdmin.getId(), adminPassword);

        // Assign the ADMIN role
        roleRepository.findByName("ADMIN").ifPresent(adminRole -> {
            savedAdmin.getRoles().add(adminRole);
            userRepository.save(savedAdmin);
        });

        log.info("Seed: created admin user '{}' with ADMIN role.", adminUsername);
        log.warn("Seed admin account active — ensure SEED_ENABLED=false and change credentials before production use.");
    }
}
