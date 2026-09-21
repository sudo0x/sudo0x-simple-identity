package com.sudo0x.simple.identity.authentication.service;

import com.sudo0x.simple.identity.common.config.AppProperties;
import com.sudo0x.simple.identity.common.security.SecurityPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
public class JwtService {

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_USERNAME = "username";

    private final AppProperties props;
    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    public JwtService(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        String privLocation = props.jwt().privateKeyLocation();
        String pubLocation  = props.jwt().publicKeyLocation();

        if ("generate".equalsIgnoreCase(privLocation) || "generate".equalsIgnoreCase(pubLocation)) {
            log.warn("Generating ephemeral RSA key pair — suitable for development ONLY. "
                   + "Set JWT_PRIVATE_KEY_LOCATION and JWT_PUBLIC_KEY_LOCATION for production.");
            generateEphemeralKeyPair();
        } else {
            loadKeysFromFiles(privLocation, pubLocation);
        }
    }

    public String generateAccessToken(SecurityPrincipal principal) {
        Instant now = Instant.now();
        Instant exp = now.plus(props.jwt().accessTokenExpiration());

        return Jwts.builder()
                .issuer(props.jwt().issuer())
                .subject(principal.userId().toString())
                .claim(CLAIM_USERNAME, principal.username())
                .claim(CLAIM_ROLES, principal.roles())
                .claim(CLAIM_PERMISSIONS, principal.permissions())
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(privateKey)
                .compact();
    }

    public Optional<SecurityPrincipal> validateAndParse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UUID userId = UUID.fromString(claims.getSubject());
            String username = claims.get(CLAIM_USERNAME, String.class);

            @SuppressWarnings("unchecked")
            List<String> rolesList = claims.get(CLAIM_ROLES, List.class);
            @SuppressWarnings("unchecked")
            List<String> permissionsList = claims.get(CLAIM_PERMISSIONS, List.class);

            Set<String> roles = rolesList != null ? new HashSet<>(rolesList) : Set.of();
            Set<String> permissions = permissionsList != null ? new HashSet<>(permissionsList) : Set.of();

            return Optional.of(new SecurityPrincipal(userId, username, roles, permissions));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    // -------------------------------------------------------------------------
    // Key loading
    // -------------------------------------------------------------------------

    private void generateEphemeralKeyPair() {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048, new SecureRandom());
            KeyPair pair = gen.generateKeyPair();
            this.privateKey = (RSAPrivateKey) pair.getPrivate();
            this.publicKey  = (RSAPublicKey)  pair.getPublic();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generation failed", e);
        }
    }

    private void loadKeysFromFiles(String privatePath, String publicPath) {
        try {
            ResourceLoader loader = new DefaultResourceLoader();
            this.privateKey = (RSAPrivateKey) loadPrivateKey(loader, privatePath);
            this.publicKey  = (RSAPublicKey)  loadPublicKey(loader, publicPath);
            log.info("RSA keys loaded from configuration");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load JWT signing keys", e);
        }
    }

    private PrivateKey loadPrivateKey(ResourceLoader loader, String location) throws Exception {
        byte[] bytes = readPem(loader, location);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(bytes);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }

    private PublicKey loadPublicKey(ResourceLoader loader, String location) throws Exception {
        byte[] bytes = readPem(loader, location);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(bytes);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private byte[] readPem(ResourceLoader loader, String location) throws Exception {
        try (InputStream is = loader.getResource(location).getInputStream()) {
            String content = new String(is.readAllBytes())
                    .replaceAll("-----BEGIN.*?-----", "")
                    .replaceAll("-----END.*?-----", "")
                    .replaceAll("\\s", "");
            return Base64.getDecoder().decode(content);
        }
    }
}
