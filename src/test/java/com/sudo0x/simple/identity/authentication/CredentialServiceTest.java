package com.sudo0x.simple.identity.authentication;

import com.sudo0x.simple.identity.common.config.AppProperties;
import com.sudo0x.simple.identity.common.exception.PasswordPolicyException;
import com.sudo0x.simple.identity.credential.repository.CredentialRepository;
import com.sudo0x.simple.identity.credential.service.CredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class CredentialServiceTest {

    @Mock CredentialRepository credentialRepository;

    private CredentialService credentialService;

    @BeforeEach
    void setUp() {
        PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        AppProperties props = new AppProperties(
                new AppProperties.JwtProperties("test", Duration.ofMinutes(15), Duration.ofDays(30), "generate", "generate"),
                new AppProperties.SecurityProperties(5, Duration.ofMinutes(15),
                        new AppProperties.SecurityProperties.CorsProperties(List.of(), List.of(), List.of(), 3600)),
                new AppProperties.PasswordProperties(8, true, true, true, false),
                new AppProperties.EmailProperties(Duration.ofHours(24), Duration.ofHours(1), false),
                new AppProperties.SeedProperties(false, "admin", "admin@localhost", ""),
                new AppProperties.CookieProperties(false, true, true, "Lax", "", "/", "access_token", "refresh_token")
        );
        credentialService = new CredentialService(credentialRepository, encoder, props);
    }

    @Test
    void validPasswordPassesPolicy() {
        assertThatCode(() -> credentialService.validatePasswordPolicy("Password1"))
                .doesNotThrowAnyException();
    }

    @Test
    void shortPasswordFailsPolicy() {
        assertThatThrownBy(() -> credentialService.validatePasswordPolicy("Abc1"))
                .isInstanceOf(PasswordPolicyException.class)
                .hasMessageContaining("at least 8");
    }

    @Test
    void noUppercaseFailsPolicy() {
        assertThatThrownBy(() -> credentialService.validatePasswordPolicy("password1"))
                .isInstanceOf(PasswordPolicyException.class)
                .hasMessageContaining("uppercase");
    }

    @Test
    void noDigitFailsPolicy() {
        assertThatThrownBy(() -> credentialService.validatePasswordPolicy("Password"))
                .isInstanceOf(PasswordPolicyException.class)
                .hasMessageContaining("digit");
    }

    @Test
    void nullPasswordFailsPolicy() {
        assertThatThrownBy(() -> credentialService.validatePasswordPolicy(null))
                .isInstanceOf(PasswordPolicyException.class);
    }
}
