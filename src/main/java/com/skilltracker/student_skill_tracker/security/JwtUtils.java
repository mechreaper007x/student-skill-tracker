package com.skilltracker.student_skill_tracker.security;

import java.util.Arrays;
import java.util.Date;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;

@Component
public class JwtUtils {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);
    private static final String INSECURE_DEFAULT_SECRET = "defaultSecretKeyForDevelopmentPurposeOnlyYouShouldChangeThisInProduction";
    private static final int MIN_SECRET_LENGTH = 32;

    private final Environment environment;

    @Value("${jwt.secret:" + INSECURE_DEFAULT_SECRET + "}")
    private String secret;

    @Value("${jwt.expiration:86400000}") // 24 hours
    private long jwtExpiration;

    public JwtUtils(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validateSecretConfiguration() {
        boolean usingDefaultSecret = INSECURE_DEFAULT_SECRET.equals(secret);
        int secretLength = (secret != null) ? secret.length() : 0;

        logger.info("JWT Configuration: length={}, usingDefault={}", secretLength, usingDefaultSecret);

        if (secret == null || secret.isBlank()) {
            logger.error("CRITICAL: jwt.secret is null or blank!");
            throw new IllegalStateException("jwt.secret must be configured");
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            logger.error("CRITICAL: jwt.secret is too short ({} chars), min is {}", secret.length(), MIN_SECRET_LENGTH);
            throw new IllegalStateException("jwt.secret must be at least 32 characters long");
        }

        boolean prodProfileActive = Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));

        if (usingDefaultSecret && prodProfileActive) {
            logger.error("CRITICAL: Insecure default jwt.secret detected while 'prod' profile is active!");
            throw new IllegalStateException("Insecure default jwt.secret is not allowed in production profiles");
        }
        
        if (usingDefaultSecret) {
            logger.warn("Using development default jwt.secret. Configure JWT_SECRET before production deployment.");
        } else {
            logger.info("JWT secret successfully configured from external source.");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            logger.debug("Failed to parse JWT claims: {}", e.getMessage());
            throw e;
        }
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(UserDetails userDetails) {
        return createToken(userDetails.getUsername());
    }

    private String createToken(String subject) {
        return Jwts.builder()
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equalsIgnoreCase(userDetails.getUsername()) && !isTokenExpired(token);
    }
}
