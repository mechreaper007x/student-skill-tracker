package com.skilltracker.student_skill_tracker.config;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.skilltracker.student_skill_tracker.repository.StudentRepository;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class CustomAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomAuthenticationFailureHandler.class);

    private final StudentRepository studentRepository;

    public CustomAuthenticationFailureHandler(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String username = request.getParameter("email");
        if (username == null) {
            username = request.getParameter("username");
        }

        log.warn("Login failure for userAttempt={}, reason={}", username, exception.getMessage());

        // Redirect back to login with error flag
        response.sendRedirect("/login.html?error=true");
    }
}
