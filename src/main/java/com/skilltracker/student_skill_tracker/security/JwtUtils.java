package com.skilltracker.student_skill_tracker.security;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtils {

    @Value("${jwt.secret:defaultSecretKeyForDevelopmentPurposeOnlyYouShouldChangeThisInProduction}")
    private String secret;

    @Value("${jwt.expiration:86400000}") // 24 hours
    private long jwtExpiration;

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
            System.out.println("DEBUG: Failed to extract claims from token: " + e.getMessage());
            throw e;
        }
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // --- Multi-Fingerprinting Additions ---
    
    public String generateSecureFingerprint() {
        byte[] randomBytes = new byte[32];
        new java.security.SecureRandom().nextBytes(randomBytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashString(String value) {
        if (value == null) return null;
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash value for fingerprinting", e);
        }
    }

    public String generateToken(UserDetails userDetails, String cookieFgp, String userAgent) {
        Map<String, Object> claims = new HashMap<>();
        if (cookieFgp != null) {
            claims.put("fgp", hashString(cookieFgp)); // Secure HttpOnly Cookie hash
        }
        if (userAgent != null) {
            claims.put("uah", hashString(userAgent)); // User-Agent hash
        }
        return createToken(claims, userDetails.getUsername());
    }

    // Retained for backward compatibility if needed, but we use the multi-fgp one now
    public String generateToken(UserDetails userDetails) {
        return generateToken(userDetails, null, null);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails, String cookieFgp, String userAgent) {
        final String username = extractUsername(token);
        boolean result = (username.equalsIgnoreCase(userDetails.getUsername()) && !isTokenExpired(token));
        
        if (!result) return false;

        // Multi-Fingerprint validation
        String tokenFgpHash = extractClaim(token, claims -> claims.get("fgp", String.class));
        String tokenUahHash = extractClaim(token, claims -> claims.get("uah", String.class));
        
        // 1. Validate Cookie Fingerprint
        if (tokenFgpHash != null) {
            if (cookieFgp == null) {
                System.out.println("DEBUG: Missing __Secure-Fgp cookie for token validation.");
                return false;
            }
            String currentFgpHash = hashString(cookieFgp);
            if (!java.security.MessageDigest.isEqual(
                    currentFgpHash.getBytes(java.nio.charset.StandardCharsets.UTF_8), 
                    tokenFgpHash.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                System.out.println("DEBUG: Token FGP mismatch (Token Theft Detected).");
                return false;
            }
        }

        // 2. Validate User-Agent Hash
        if (tokenUahHash != null) {
            if (userAgent == null) {
                System.out.println("DEBUG: Missing User-Agent for token validation.");
                return false;
            }
            String currentUahHash = hashString(userAgent);
            if (!java.security.MessageDigest.isEqual(
                    currentUahHash.getBytes(java.nio.charset.StandardCharsets.UTF_8), 
                    tokenUahHash.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                System.out.println("DEBUG: Token User-Agent mismatch (Token Theft Detected).");
                return false;
            }
        }

        return true;
    }

    // For backwards compatibility during transition or testing
    public Boolean validateToken(String token, UserDetails userDetails) {
        return validateToken(token, userDetails, null, null);
    }
}
