package com.be_ai_learning_platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtUtil {

    // base64 >= 256bit
    private static final String SECRET_KEY =
            "bXlfc3VwZXJfc2VjcmV0X2tleV93aGljaF9pc19hdF9sZWFzdF8zMl9jaGFyYWN0ZXJz";

    private static final long EXPIRATION_TIME =
            1000 * 60 * 60 * 24; // 1 day

    // ================== GENERATE ==================
    public String generateToken(String email, List<String> roles) {

        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", roles);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(
                        new Date(System.currentTimeMillis() + EXPIRATION_TIME)
                )
                .signWith(getSigningKey())
                .compact();
    }

    // ================== KEY ==================
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // ================== EXTRACT ==================
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractAllClaims(token).get("roles", List.class);
    }

    private <T> T extractClaim(
            String token,
            Function<Claims, T> resolver
    ) {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // ================== VALIDATE ==================
    public boolean validateToken(String token) {
        return !extractClaim(token, Claims::getExpiration)
                .before(new Date());
    }
}
