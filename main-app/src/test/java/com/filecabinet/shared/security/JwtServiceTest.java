package com.filecabinet.shared.security;

import com.filecabinet.user.model.Role;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-that-is-long-enough-for-hmac-sha-signing-key-123456";

    private AppUserDetails user() {
        return new AppUserDetails(UUID.randomUUID(), "alice", "hash", Role.CLERK);
    }

    @Test
    void generatedTokenCarriesUsernameAndValidates() {
        JwtService service = new JwtService(SECRET, 3_600_000);
        AppUserDetails user = user();

        String token = service.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(service.isValid(token)).isTrue();
        assertThat(service.extractUsername(token)).isEqualTo("alice");
    }

    @Test
    void garbageTokenIsInvalid() {
        JwtService service = new JwtService(SECRET, 3_600_000);
        assertThat(service.isValid("not-a-real-token")).isFalse();
    }

    @Test
    void expiredTokenIsInvalid() {
        JwtService service = new JwtService(SECRET, -1_000);
        String token = service.generateToken(user());
        assertThat(service.isValid(token)).isFalse();
    }
}
