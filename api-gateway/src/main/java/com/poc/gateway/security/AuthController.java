package com.poc.gateway.security;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Real credential-backed auth for the gateway's own users (backed by
 * app_user in auth_db, bcrypt-hashed passwords). This is one of two ways
 * to get a token the gateway will accept - the other is a real OIDC login
 * against Keycloak (see README/OidcJwtValidator), which issues its own
 * RS256 token directly; that flow never touches this controller.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(JwtUtil jwtUtil, AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<Map<String, String>>> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().body(Map.of("error", "username and password are required")));
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));

        return userRepository.save(user)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(Map.of("username", saved.getUsername())))
                .onErrorResume(DuplicateKeyException.class,
                        e -> Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "username already exists"))));
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, String>>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        if (username == null || password == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid credentials")));
        }

        return userRepository.findByUsername(username)
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .map(user -> ResponseEntity.ok(Map.of("token", jwtUtil.generateToken(user.getUsername()))))
                .defaultIfEmpty(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid credentials")));
    }
}
