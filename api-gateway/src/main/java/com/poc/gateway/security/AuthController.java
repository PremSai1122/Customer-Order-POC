package com.poc.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * The only way to get a token the gateway will accept. Credentials are
 * checked against the jwt.users map in application.yml - there is no user
 * database and no identity provider behind this.
 *
 * <p>Neither the username nor the password is ever logged, matching the
 * PII-safe logging in the other services: the logs record that an attempt
 * happened and whether it succeeded, nothing that identifies who made it.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final JwtUtil jwtUtil;
    private final JwtProperties properties;

    public AuthController(JwtUtil jwtUtil, JwtProperties properties) {
        this.jwtUtil = jwtUtil;
        this.properties = properties;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, String>>> login(@RequestBody Map<String, String> request) {
        log.info("Login attempt received");

        String username = request.get("username");
        String password = request.get("password");

        if (username == null || password == null) {
            log.warn("Login failed - request was missing a username or password");
            return Mono.just(unauthorized());
        }

        String expected = properties.getUsers().get(username);
        if (expected == null || !matches(expected, password)) {
            log.warn("Login failed - invalid credentials");
            return Mono.just(unauthorized());
        }

        log.info("Login succeeded");
        return Mono.just(ResponseEntity.ok(Map.of("token", jwtUtil.generateToken(username))));
    }

    /** Constant-time compare so a wrong password cannot be narrowed down by timing. */
    private boolean matches(String expected, String supplied) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }

    private ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid credentials"));
    }
}
