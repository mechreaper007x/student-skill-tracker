package com.skilltracker.student_skill_tracker.config;

import static org.springframework.security.config.Customizer.*;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.skilltracker.student_skill_tracker.security.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

        private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

        @Bean
        public static PasswordEncoder passwordEncoder() {
                return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        }

        private final JwtAuthenticationFilter jwtAuthFilter;
        private final List<String> allowedOriginPatterns;

        public SecurityConfig(
                        JwtAuthenticationFilter jwtAuthFilter,
                        @Value("${app.security.allowed-origin-patterns:http://localhost:4200,http://127.0.0.1:4200}") String allowedOriginPatterns) {
                this.jwtAuthFilter = jwtAuthFilter;
                List<String> parsedOrigins = Arrays.stream(allowedOriginPatterns.split(","))
                                .map(String::trim)
                                .filter(origin -> !origin.isEmpty())
                                .toList();
                this.allowedOriginPatterns = parsedOrigins.isEmpty()
                                ? List.of("http://localhost:4200", "http://127.0.0.1:4200")
                                : parsedOrigins;
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
                return config.getAuthenticationManager();
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .csrf(csrf -> csrf.disable()) // Stateless JWT typically disables CSRF when the token is
                                                              // in headers
                                .formLogin(form -> form.disable())
                                .httpBasic(httpBasic -> httpBasic.disable())
                                .logout(logout -> logout.disable())
                                .requestCache(requestCache -> requestCache.disable())
                                .headers(headers -> headers
                                                .contentTypeOptions(withDefaults())
                                                .frameOptions(frame -> frame.sameOrigin())
                                                .referrerPolicy(referrer -> referrer
                                                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                                                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                                                "camera=(), microphone=(), geolocation=()"))
                                                .contentSecurityPolicy(csp -> csp.policyDirectives(
                                                                "default-src 'self'; " +
                                                                                "script-src 'self' 'unsafe-inline' 'unsafe-eval'; "
                                                                                +
                                                                                "style-src 'self' 'unsafe-inline'; " +
                                                                                "img-src 'self' data: https:; " +
                                                                                "connect-src 'self' https: wss: ws:; " +
                                                                                "object-src 'none'; " +
                                                                                "base-uri 'self'; " +
                                                                                "form-action 'self'; " +
                                                                                "frame-ancestors 'self';")))
                                .exceptionHandling(exception -> exception
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        logger.debug("Unauthenticated request to {}",
                                                                        request.getRequestURI());
                                                        response.setStatus(
                                                                        jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                                                        response.setContentType("application/json");
                                                        response.getWriter().write(
                                                                        "{\"error\": \"Unauthorized\", \"message\": \"Authentication is required to access this resource\"}");
                                                }))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/", "/login", "/login.html",
                                                                "/register.html", "/api/students/register",
                                                                "/api/auth/**",
                                                                "/forgot-password.html", "/reset-password.html",
                                                                "/api/auth/forgot-password", "/api/auth/reset-password",
                                                                "/register", "/forgot-password", "/reset-password",
                                                                "/dashboard", "/skills", "/advisor", "/leaderboard",
                                                                "/arsenal", "/proving-grounds", "/duel-arena",
                                                                "/cognitive-sprint", "/compiler", "/settings",
                                                                "/battle-station",
                                                                "/error",
                                                                "/api/ping",
                                                                "/actuator/health", "/actuator/health/**",
                                                                "/css/**", "/js/**", "/images/**",
                                                                "/index.html", "/favicon.ico", "/*.js", "/*.css",
                                                                "/assets/**", "/ws/**", "/ws")
                                                .permitAll()
                                                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**")
                                                .permitAll()
                                                .requestMatchers("/api/debug/**").hasRole("ADMIN")
                                                .anyRequest().authenticated())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOriginPatterns(allowedOriginPatterns);
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin",
                                "X-Requested-With", "Cache-Control", "Pragma"));
                configuration.setAllowCredentials(false);
                configuration.setMaxAge(3600L);
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}
