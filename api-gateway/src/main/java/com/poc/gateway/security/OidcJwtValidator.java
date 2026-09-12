package com.poc.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Validates tokens issued by the real Keycloak realm (RS256, verified
 * against Keycloak's published JWKS) - the second of the two accepted
 * token kinds, alongside the gateway's own self-issued HS256 tokens from
 * JwtUtil/AuthController.
 */
@Component
public class OidcJwtValidator {

    private final ReactiveJwtDecoder jwtDecoder;

    public OidcJwtValidator(@Value("${keycloak.jwk-set-uri}") String jwkSetUri) {
        this.jwtDecoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
    }

    public Mono<Jwt> validate(String token) {
        return jwtDecoder.decode(token);
    }
}
