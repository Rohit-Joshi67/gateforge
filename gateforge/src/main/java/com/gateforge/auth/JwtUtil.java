package com.gateforge.auth;

import com.gateforge.routing.GatewayProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final int expirationMinutes;

    public JwtUtil(GatewayProperties gatewayProperties) {
        this.key = Keys.hmacShaKeyFor(gatewayProperties.getJwt().getSecret().getBytes());
        this.expirationMinutes = gatewayProperties.getJwt().getExpirationMinutes();
    }

    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000L * 60 * expirationMinutes))
                .signWith(key)
                .compact();
    }

    public String validateAndGetSubject(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
