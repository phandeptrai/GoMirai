package com.gomirai.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared JWT Service for all GoMirai microservices
 * 
 * Handles:
 * - JWT token generation (AuthService only)
 * - JWT token parsing and validation (All services)
 * - Secret key management
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlMillis;
    private final JwtParser jwtParser;

    /**
     * Constructor with secret and TTL
     * Used by all services for token generation and validation
     */
    public JwtService(
        @Value("${security.jwt.secret}") String secret,
        @Value("${security.jwt.access-ttl-ms:86400000}") long accessTtlMillis
    ) {
        // Handle both BASE64 and plain text secrets
        byte[] secretBytes = secret.startsWith("BASE64:")
            ? Decoders.BASE64.decode(secret.substring("BASE64:".length()))
            : secret.getBytes(StandardCharsets.UTF_8);
        
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessTtlMillis = accessTtlMillis;
        this.jwtParser = Jwts.parser().verifyWith(key).build();
    }

    /**
     * Generate JWT token (AuthService only)
     */
    public String generateToken(UUID userId, String role) {
        Instant now = Instant.now();
        Instant exp = now.plusMillis(accessTtlMillis);
        
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("role", role)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Parse and validate JWT token
     * Returns Optional.empty() if token is invalid or expired
     */
    public Optional<Claims> parseToken(String token) {
        try {
            return Optional.of(jwtParser.parseSignedClaims(token).getPayload());
        } catch (Exception e) {
            // Token is invalid, expired, or malformed
            return Optional.empty();
        }
    }

    /**
     * Extract user ID from token
     */
    public Optional<UUID> extractUserId(String token) {
        return parseToken(token)
                .map(Claims::getSubject)
                .flatMap(subject -> {
                    try {
                        return Optional.of(UUID.fromString(subject));
                    } catch (IllegalArgumentException e) {
                        return Optional.empty();
                    }
                });
    }

    /**
     * Extract role from token
     */
    public Optional<String> extractRole(String token) {
        return parseToken(token)
                .map(claims -> claims.get("role", String.class));
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        return parseToken(token)
                .map(Claims::getExpiration)
                .map(expiration -> expiration.before(new Date()))
                .orElse(true);
    }

    /**
     * Validate token
     */
    public boolean validateToken(String token) {
        Optional<Claims> claims = parseToken(token);
        if (claims.isEmpty()) {
            return false;
        }
        Date exp = claims.get().getExpiration();
        return exp == null || !exp.before(new Date());
    }
}

