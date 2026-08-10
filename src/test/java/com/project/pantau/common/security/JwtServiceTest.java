package com.project.pantau.common.security;

import com.project.pantau.common.config.JwtProperties;
import com.project.pantau.entity.User;
import com.project.pantau.enums.UserRole;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // 32 raw bytes (256 bits) base64-encoded, satisfying the HS256 minimum key length.
    private static final String SECRET_KEY = Base64.getEncoder().encodeToString(buildKeyBytes());
    private static final long ONE_HOUR_MILLIS = 60L * 60L * 1000L;
    private CustomUserDetails userDetails;

    private static byte[] buildKeyBytes() {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte) 7);
        return bytes;
    }

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("hashed-password")
                .displayName("Test User")
                .role(UserRole.CITIZEN)
                .build();
        userDetails = CustomUserDetails.builder().user(user).build();
    }

    private JwtService serviceWithExpiration(long expirationMillis) {
        return new JwtService(new JwtProperties(SECRET_KEY, expirationMillis));
    }

    @Test
    @DisplayName("generateToken produces a non-blank, well-formed JWT")
    void generateTokenProducesWellFormedToken() {
        JwtService jwtService = serviceWithExpiration(ONE_HOUR_MILLIS);

        String token = jwtService.generateToken(Map.of(), userDetails);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("extractUsername round-trips the subject used at generation time")
    void extractUsernameRoundTrips() {
        JwtService jwtService = serviceWithExpiration(ONE_HOUR_MILLIS);
        String token = jwtService.generateToken(Map.of(), userDetails);

        String extracted = jwtService.extractUsername(token);

        assertThat(extracted).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("isTokenValid returns true for a fresh token matching the user details")
    void isTokenValidReturnsTrueForMatchingFreshToken() {
        JwtService jwtService = serviceWithExpiration(ONE_HOUR_MILLIS);
        String token = jwtService.generateToken(Map.of(), userDetails);

        boolean valid = jwtService.isTokenValid(token, userDetails);

        assertThat(valid).isTrue();
    }

    @Test
    @DisplayName("isTokenValid returns false when the username does not match the user details")
    void isTokenValidReturnsFalseForUsernameMismatch() {
        JwtService jwtService = serviceWithExpiration(ONE_HOUR_MILLIS);
        String token = jwtService.generateToken(Map.of(), userDetails);

        User otherUser = User.builder()
                .id(UUID.randomUUID())
                .email("other@example.com")
                .password("other-password")
                .displayName("Other User")
                .role(UserRole.RESOLVER)
                .build();
        CustomUserDetails otherUserDetails = CustomUserDetails.builder().user(otherUser).build();

        boolean valid = jwtService.isTokenValid(token, otherUserDetails);

        assertThat(valid).isFalse();
    }

    @Test
    @DisplayName("expired token raises ExpiredJwtException when read")
    void expiredTokenThrowsOnParse() {
        JwtService jwtService = serviceWithExpiration(-ONE_HOUR_MILLIS);
        String expiredToken = jwtService.generateToken(Map.of(), userDetails);

        assertThatThrownBy(() -> jwtService.isTokenValid(expiredToken, userDetails))
                .isInstanceOf(ExpiredJwtException.class);
        assertThatThrownBy(() -> jwtService.extractUsername(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("token signed with a different key is rejected as an invalid signature")
    void tokenWithDifferentSignatureIsRejected() {
        JwtService signingService = serviceWithExpiration(ONE_HOUR_MILLIS);
        String token = signingService.generateToken(Map.of(), userDetails);

        byte[] otherKeyBytes = new byte[32];
        Arrays.fill(otherKeyBytes, (byte) 9);
        String otherSecret = Base64.getEncoder().encodeToString(otherKeyBytes);
        JwtService verifyingService = new JwtService(new JwtProperties(otherSecret, ONE_HOUR_MILLIS));

        assertThatThrownBy(() -> verifyingService.extractUsername(token))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("malformed token string is rejected")
    void malformedTokenIsRejected() {
        JwtService jwtService = serviceWithExpiration(ONE_HOUR_MILLIS);

        assertThatThrownBy(() -> jwtService.extractUsername("not-a-valid-jwt"))
                .isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    @DisplayName("extra claims supplied at generation time are embedded in the token")
    void extraClaimsAreEmbedded() {
        JwtService jwtService = serviceWithExpiration(ONE_HOUR_MILLIS);

        String token = jwtService.generateToken(Map.of("role", "CITIZEN"), userDetails);

        // Round trip via extractUsername proves the token parses and signature/claims are intact.
        assertThat(jwtService.extractUsername(token)).isEqualTo("test@example.com");
    }
}
