package com.skilltracker.student_skill_tracker.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtils jwtUtils, UserDetailsService userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        System.out.println("--- JWT Filter Start ---");
        System.out.println("Request URI: " + request.getRequestURI());
        
        if (authHeader == null) {
            System.out.println("DEBUG: No Authorization header found.");
            filterChain.doFilter(request, response);
            return;
        }

        System.out.println("DEBUG: Auth Header found: [" + authHeader.substring(0, Math.min(authHeader.length(), 20)) + "...]");
        
        if (!authHeader.toLowerCase().startsWith("bearer ")) {
            System.out.println("DEBUG: Header does NOT start with 'bearer '. Header value: [" + authHeader + "]");
            // Log character codes to see if there are hidden characters
            StringBuilder sb = new StringBuilder("Char codes: ");
            for(int i=0; i<Math.min(authHeader.length(), 10); i++) {
                sb.append((int)authHeader.charAt(i)).append(" ");
            }
            System.out.println("DEBUG: " + sb.toString());
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = authHeader.substring(7).trim();
            System.out.println("DEBUG: Trimmed JWT starts with: [" + (jwt.length() > 10 ? jwt.substring(0, 10) : jwt) + "]");

            if (jwt.startsWith("\"") && jwt.endsWith("\"") && jwt.length() > 2) {
                System.out.println("DEBUG: Stripping quotes from JWT.");
                jwt = jwt.substring(1, jwt.length() - 1);
            }

            final String jwtFinal = jwt;
            final String userEmail = jwtUtils.extractUsername(jwtFinal);
            System.out.println("DEBUG: Extracted email: " + userEmail);

            if (userEmail != null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                System.out.println("DEBUG: Loaded UserDetails for: " + userDetails.getUsername());
                
                boolean isValid = jwtUtils.validateToken(jwtFinal, userDetails);
                System.out.println("DEBUG: isValid: " + isValid);

                if (isValid) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    System.out.println("DEBUG: Authentication successful, context set.");
                } else {
                    System.out.println("DEBUG: Validation failed in JwtUtils.");
                }
            } else {
                System.out.println("DEBUG: userEmail is null after extraction.");
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Exception in JWT Filter: " + e.getMessage());
            e.printStackTrace();
            handleError(response, "Authentication error: " + e.getMessage());
            return;
        }

        System.out.println("--- JWT Filter End ---");
        filterChain.doFilter(request, response);
    }

    private void handleError(HttpServletResponse response, String message) throws java.io.IOException {
        System.out.println("DEBUG: Authentication Failure - " + message);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"Unauthorized\", \"message\": \"" + message + "\"}");
    }
}
