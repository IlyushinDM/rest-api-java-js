package org.example.education.util;

import org.example.education.config.ServerConfig;
import org.example.education.model.UserType;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

public class JwtUtil {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    private final SecretKey secretKey;
    private final String issuer;
    private final long expirationMillis;

    public JwtUtil() {
        String configuredSecret = ServerConfig.getJwtSecretKey();
        this.secretKey = Keys.hmacShaKeyFor(configuredSecret.getBytes(StandardCharsets.UTF_8));
        this.issuer = ServerConfig.getJwtIssuer();
        this.expirationMillis = ServerConfig.getJwtExpirationMillis();
    }

    public String generateToken(int userId, String email, UserType userType, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .setSubject(Integer.toString(userId))
                .claim("email", email)
                .claim("userType", userType.toString())
                .claim("role", role)
                .setIssuer(issuer)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Optional<Claims> validateTokenAndGetClaims(String token) {
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        try {
            Jws<Claims> jwsClaims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseClaimsJws(token);
            return Optional.of(jwsClaims.getBody());
        } catch (ExpiredJwtException e) {
            logger.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.warn("JWT token is malformed: {}", e.getMessage());
        } catch (SignatureException e) {
            logger.warn("JWT signature validation failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.warn("JWT claims string is empty or null or other argument error: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error during JWT validation", e);
        }
        return Optional.empty();
    }

    public UserType getUserTypeFromClaims(Claims claims) {
        String userTypeStr = claims.get("userType", String.class);
        try {
            return UserType.valueOf(userTypeStr);
        } catch (IllegalArgumentException | NullPointerException e) {
            logger.warn("Invalid or missing userType in JWT claims: {}", userTypeStr, e);
            return null;
        }
    }

    public String getRoleFromClaims(Claims claims) {
        return claims.get("role", String.class);
    }

    public int getUserIdFromClaims(Claims claims) {
        return Integer.parseInt(claims.getSubject());
    }

    public String getEmailFromClaims(Claims claims) {
        return claims.get("email", String.class);
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }
}