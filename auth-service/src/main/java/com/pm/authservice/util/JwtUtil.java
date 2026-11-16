package com.pm.authservice.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    private final Key secrertKey;

    public JwtUtil(@Value("${jwt.secret:${JWT_SECRET:dGhpc2lzYXZlcnlzZWNyZXRrZXlmb3Jqd3RhdXRoZW50aWNhdGlvbjEyMzQ1Njc4OTA=}}") String secret) {
        byte[] keyBytes;
        try {
            byte[] decoded = Base64.getDecoder().decode(secret.getBytes(StandardCharsets.UTF_8));
            keyBytes = (decoded.length >= 32) ? decoded : secret.getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.secrertKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 *10))
                .signWith(secrertKey)
                .compact();
    }
}
