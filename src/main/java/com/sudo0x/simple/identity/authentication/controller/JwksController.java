package com.sudo0x.simple.identity.authentication.controller;

import com.sudo0x.simple.identity.authentication.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Exposes the RSA public key as a JWKS (JSON Web Key Set) document.
 * Consuming services use this to validate JWTs without calling the Identity Service on every request.
 *
 * Example usage in a consuming Spring Boot app:
 *   spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://identity-service/.well-known/jwks.json
 */
@RestController
@RequestMapping("/.well-known")
@RequiredArgsConstructor
@Tag(name = "JWKS", description = "Public key discovery for JWT validation")
public class JwksController {

    private final JwtService jwtService;

    @GetMapping("/jwks.json")
    @Operation(summary = "JSON Web Key Set — returns the RSA public key used to verify JWTs")
    public Map<String, Object> jwks() {
        RSAPublicKey publicKey = jwtService.getPublicKey();
        return Map.of("keys", List.of(buildJwk(publicKey)));
    }

    private Map<String, Object> buildJwk(RSAPublicKey key) {
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "n",   enc.encodeToString(toUnsignedBytes(key.getModulus())),
                "e",   enc.encodeToString(toUnsignedBytes(key.getPublicExponent()))
        );
    }

    private byte[] toUnsignedBytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        // BigInteger.toByteArray() may prefix a 0x00 byte to indicate positive sign; strip it
        if (bytes[0] == 0) {
            byte[] stripped = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, stripped, 0, stripped.length);
            return stripped;
        }
        return bytes;
    }
}
