package com.skilltracker.student_skill_tracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CustomAuthenticationFailureHandler failureHandler) throws Exception {

        http
            // Enable CSRF and store token in cookie (readable by JS)
            .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            // Allow unauthenticated POSTs for login and API registration (frontend posts without CSRF token)
            .ignoringRequestMatchers("/login", "/api/students/register", "/api/debug/**")
)


            // Login page configuration
            .formLogin(form -> form
            .loginPage("/login.html")
            .loginProcessingUrl("/login")
            .usernameParameter("email")  // your login.html uses name="username"
            .passwordParameter("password")
            // wire failure handler to log failed attempts
            .failureHandler(failureHandler)
            .defaultSuccessUrl("/dashboard.html", true) // or use your success handler
            .permitAll()
)


            // Logout configuration
            .logout(logout -> logout
                .logoutUrl("/logout")                         // POST /logout
                .logoutSuccessUrl("/login.html?logout")       // Redirect after logout
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")                  // Kill session cookie
            )

            // Public endpoints
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/login", "/login.html",
                    "/register.html", "/api/students/register",
                    "/forgot-password.html", "/reset-password.html",
                    "/api/auth/forgot-password", "/api/auth/reset-password",
                    "/css/**", "/js/**", "/images/**", "/api/debug/**"
                ).permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
