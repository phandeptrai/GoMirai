package com.gomirai.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlMillis;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.access-ttl-ms:3600000}") long accessTtlMillis) {
        byte[] secretBytes = secret.startsWith("BASE64:")
            ? Decoders.BASE64.decode(secret.substring("BASE64:".length()))
            : secret.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.accessTtlMillis = accessTtlMillis;
    }

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

    public Optional<Claims> parseToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return Optional.of(claims);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}



