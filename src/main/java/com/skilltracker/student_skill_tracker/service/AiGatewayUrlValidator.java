package com.skilltracker.student_skill_tracker.service;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Validates the one OpenAI-compatible Chat Completions gateway URL.
 *
 * Only public HTTPS endpoints are accepted. This is important because gateway
 * URLs are user-controlled and the backend must not become an SSRF proxy into
 * its own network or cloud metadata services.
 */
public final class AiGatewayUrlValidator {

    public static final String DEFAULT_BASE_URL = "https://api.mistral.ai/v1";

    private AiGatewayUrlValidator() {
    }

    public static String normalizeBaseUrl(String rawBaseUrl) {
        String value = rawBaseUrl == null || rawBaseUrl.isBlank()
                ? DEFAULT_BASE_URL
                : rawBaseUrl.trim();

        final URI uri;
        try {
            uri = URI.create(value).normalize();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Gateway URL is invalid.");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "Gateway URL must be a public HTTPS base URL, for example https://api.example.com/v1.");
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if ("localhost".equals(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || "metadata.google.internal".equals(host)) {
            throw new IllegalArgumentException("Local and metadata gateway addresses are not allowed.");
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()) {
                    throw new IllegalArgumentException("Gateway URL must resolve to a public IP address.");
                }
            }
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException("Gateway hostname could not be resolved.");
        }

        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        if (path.contains("/../") || path.endsWith("/..") || path.contains("/./")) {
            throw new IllegalArgumentException("Gateway URL path is invalid.");
        }

        // Accept a pasted Chat Completions endpoint, but reject vendor-specific
        // endpoints because this application speaks one gateway contract only.
        if (path.endsWith("/messages")) {
            throw new IllegalArgumentException(
                    "Use an OpenAI-compatible Chat Completions gateway URL, not a vendor-specific Messages endpoint.");
        }
        if (path.endsWith("/chat/completions")) {
            path = path.substring(0, path.length() - "/chat/completions".length());
        }
        while (path.endsWith("/") && !path.isEmpty()) {
            path = path.substring(0, path.length() - 1);
        }

        try {
            return new URI("https", null, host, uri.getPort(), path, null, null).toASCIIString();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Gateway URL is invalid.");
        }
    }

    public static String chatCompletionsUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        return normalized + "/chat/completions";
    }

    public static String modelsUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        return normalized + "/models";
    }

    public static String userModelsUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        return normalized + "/models/user";
    }
}
