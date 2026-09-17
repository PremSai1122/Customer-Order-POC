package com.poc.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binds the jwt.* block in application.yml. A @ConfigurationProperties class
 * rather than @Value because jwt.users is a map, which @Value cannot bind
 * without resorting to SpEL string parsing.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** Symmetric HS256 secret. Signs and verifies; must be >= 32 characters. */
    private String secret;

    /** Token lifetime in milliseconds. */
    private long expirationMs;

    /** username -> password, checked as plain text by AuthController. */
    private Map<String, String> users = new LinkedHashMap<>();

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public long getExpirationMs() { return expirationMs; }
    public void setExpirationMs(long expirationMs) { this.expirationMs = expirationMs; }

    public Map<String, String> getUsers() { return users; }
    public void setUsers(Map<String, String> users) { this.users = users; }
}
