package com.sudo0x.simple.identity.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudo0x.simple.identity.authentication.dto.LoginRequest;
import com.sudo0x.simple.identity.credential.service.CredentialService;
import com.sudo0x.simple.identity.role.repository.RoleRepository;
import com.sudo0x.simple.identity.user.entity.User;
import com.sudo0x.simple.identity.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CredentialService credentialService;
    @Autowired RoleRepository roleRepository;

    private User regularUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        regularUser = userRepository.save(new User("securitytest_user", "secuser@test.com"));
        credentialService.createCredential(regularUser.getId(), "TestPass1");
        roleRepository.findByName("USER").ifPresent(r -> {
            regularUser.getRoles().add(r);
            userRepository.save(regularUser);
        });

        adminUser = userRepository.save(new User("securitytest_admin", "secadmin@test.com"));
        credentialService.createCredential(adminUser.getId(), "AdminPass1");
        roleRepository.findByName("ADMIN").ifPresent(r -> {
            adminUser.getRoles().add(r);
            userRepository.save(adminUser);
        });
    }

    @Test
    void regularUserCannotAccessUserAdminEndpoints() throws Exception {
        String token = loginAndGetToken("securitytest_user", "TestPass1");

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUserCanAccessUserAdminEndpoints() throws Exception {
        String token = loginAndGetToken("securitytest_admin", "AdminPass1");

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void expiredJwtFails() throws Exception {
        // Malformed/garbage JWT
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.invalid.signature"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedJwtFails() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer not-a-jwt-at-all"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestWithoutBearerPrefixFails() throws Exception {
        String token = loginAndGetToken("securitytest_user", "TestPass1");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", token))  // Missing "Bearer " prefix
                .andExpect(status().isUnauthorized());
    }

    @Test
    void responseNeverContainsPasswordOrHash() throws Exception {
        String token = loginAndGetToken("securitytest_admin", "AdminPass1");

        MvcResult result = mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContainIgnoringCase("password")
                .doesNotContainIgnoringCase("passwordHash")
                .doesNotContainIgnoringCase("password_hash");
    }

    @Test
    void bruteForceLocksAccountAfterMaxAttempts() throws Exception {
        // Attempt to login with wrong password multiple times
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new LoginRequest("securitytest_user", "wrongpass"))))
                    .andExpect(status().isUnauthorized());
        }

        // After locking, even correct password should fail
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("securitytest_user", "TestPass1"))))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(username, password))))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> body = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) body.get("data");
        return data.get("accessToken");
    }
}
