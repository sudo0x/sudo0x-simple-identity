package com.sudo0x.simple.identity.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sudo0x.simple.identity.authentication.dto.LoginRequest;
import com.sudo0x.simple.identity.authentication.dto.RefreshRequest;
import com.sudo0x.simple.identity.common.response.ApiResponse;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired CredentialService credentialService;
    @Autowired RoleRepository roleRepository;

    private User testUser;
    private static final String PASSWORD = "TestPass1";

    @BeforeEach
    void setUp() {
        testUser = new User("integrationuser", "integration@test.com");
        testUser = userRepository.save(testUser);
        credentialService.createCredential(testUser.getId(), PASSWORD);

        roleRepository.findByName("USER").ifPresent(role -> {
            testUser.getRoles().add(role);
            userRepository.save(testUser);
        });
    }

    @Test
    void loginWithValidCredentialsReturnsTokens() throws Exception {
        LoginRequest request = new LoginRequest("integrationuser", PASSWORD);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        LoginRequest request = new LoginRequest("integrationuser", "wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void loginWithUnknownUserReturns401() throws Exception {
        LoginRequest request = new LoginRequest("nonexistentuser", "password123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void accessProtectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginResponseDoesNotContainPassword() throws Exception {
        LoginRequest request = new LoginRequest("integrationuser", PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(PASSWORD);
        assertThat(body).doesNotContain("passwordHash");
        assertThat(body).doesNotContain("password_hash");
    }

    @Test
    void refreshTokenRotation() throws Exception {
        // Login to get initial tokens
        LoginRequest loginRequest = new LoginRequest("integrationuser", PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> loginBody = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) loginBody.get("data");
        String refreshToken = data.get("refreshToken");

        // Refresh to get new tokens
        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> refreshBody = objectMapper.readValue(
                refreshResult.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> refreshData = (Map<String, String>) refreshBody.get("data");
        String newRefreshToken = refreshData.get("refreshToken");

        assertThat(newRefreshToken).isNotEqualTo(refreshToken);
    }

    @Test
    void reusingRevokedRefreshTokenFails() throws Exception {
        // Login
        LoginRequest loginRequest = new LoginRequest("integrationuser", PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> loginBody = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) loginBody.get("data");
        String refreshToken = data.get("refreshToken");

        // First refresh (rotates the token)
        RefreshRequest refreshRequest = new RefreshRequest(refreshToken);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk());

        // Reuse the old token — should fail
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meEndpointWithValidToken() throws Exception {
        // Login
        LoginRequest loginRequest = new LoginRequest("integrationuser", PASSWORD);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Map<?, ?> loginBody = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) loginBody.get("data");
        String accessToken = data.get("accessToken");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("integrationuser"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void jwksEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].e").isNotEmpty());
    }

    @Test
    void validationErrorReturnsStructuredErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors").isArray());
    }
}
