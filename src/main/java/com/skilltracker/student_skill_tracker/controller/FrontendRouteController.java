package com.skilltracker.student_skill_tracker.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Handles direct browser refreshes on SPA routes.
 * If the Angular bundle is packaged in Spring static resources, we forward to
 * index.html.
 * Otherwise (local dev), we redirect to the frontend dev server URL.
 */
@Controller
public class FrontendRouteController {

    private final Resource indexHtml;
    private final String frontendBaseUrl;

    public FrontendRouteController(
            ResourceLoader resourceLoader,
            @Value("${app.frontend.base-url:http://localhost:4200}") String frontendBaseUrl) {
        this.indexHtml = resourceLoader.getResource("classpath:/static/index.html");
        this.frontendBaseUrl = normalizeBaseUrl(frontendBaseUrl);
    }

    @GetMapping({
            "/",
            "/login",
            "/register",
            "/forgot-password",
            "/reset-password",
            "/dashboard",
            "/skills",
            "/advisor",
            "/leaderboard",
            "/arsenal",
            "/proving-grounds",
            "/duel-arena",
            "/cognitive-sprint",
            "/compiler",
            "/settings",
            "/battle-station",
            "/hr-dashboard",
            "/interviewer-workbench",
            "/interviewer-workbench/**"
    })
    public String spaEntry(HttpServletRequest request) {
        if (indexHtml.exists() && indexHtml.isReadable()) {
            return "forward:/index.html";
        }

        String requestUri = request.getRequestURI();
        if (requestUri == null || requestUri.isBlank()) {
            requestUri = "/";
        }
        return "redirect:" + frontendBaseUrl + requestUri;
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:4200";
        }

        String trimmed = value.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
