package com.whu.serviceauth.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtUtil {

    public static String generateToken(Long userId, String username, String role,
                                       String secret, long expiration) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiration);

        Map<String, Object> claims = Map.of(
            "userId", userId,
            "username", username,
            "role", role,
            "jti", UUID.randomUUID().toString()
        );

        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(SignatureAlgorithm.HS256, secret)
            .compact();
    }

    public static Long parseToken(String token, String secret) {
        Claims claims = Jwts.parser()
            .setSigningKey(secret)
            .parseClaimsJws(token)
            .getBody();

        return claims.get("userId", Long.class);
    }

    public static String getJti(String token, String secret) {
        Claims claims = Jwts.parser()
            .setSigningKey(secret)
            .parseClaimsJws(token)
            .getBody();
        return claims.get("jti", String.class);
    }
}
