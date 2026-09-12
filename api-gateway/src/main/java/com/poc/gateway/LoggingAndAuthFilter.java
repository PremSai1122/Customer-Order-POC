package com.poc.gateway;

import com.poc.gateway.security.JwtUtil;
import com.poc.gateway.security.OidcJwtValidator;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * This is the "pre filtering... authentication, authorization" hook the
 * transcript calls out. Runs before every proxied request (only requests
 * matched by a gateway route go through this - /auth/login and
 * /auth/register are handled by AuthController directly and never reach
 * here).
 *
 * Accepts either of two token kinds: the gateway's own self-issued HS256
 * token (from /auth/login, backed by app_user in auth_db) or a real
 * Keycloak-issued RS256 token (from a genuine OIDC login). Local
 * validation is tried first since it's a cheap in-process signature check;
 * OIDC validation involves a call out to Keycloak's JWKS endpoint.
 */
@Component
public class LoggingAndAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingAndAuthFilter.class);

    private final JwtUtil jwtUtil;
    private final OidcJwtValidator oidcJwtValidator;

    public LoggingAndAuthFilter(JwtUtil jwtUtil, OidcJwtValidator oidcJwtValidator) {
        this.jwtUtil = jwtUtil;
        this.oidcJwtValidator = oidcJwtValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var request = exchange.getRequest();
        long start = System.currentTimeMillis();

        log.info("--> {} {}", request.getMethod(), request.getURI());

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Rejected {} {} - missing/malformed Authorization header", request.getMethod(), request.getURI());
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }
        String token = authHeader.substring(7);

        boolean validLocalToken;
        try {
            jwtUtil.validateAndGetClaims(token);
            validLocalToken = true;
        } catch (JwtException e) {
            validLocalToken = false;
        }

        if (validLocalToken) {
            return proceed(exchange, chain, request, start);
        }

        return oidcJwtValidator.validate(token)
                .flatMap(jwt -> proceed(exchange, chain, request, start))
                .onErrorResume(e -> {
                    log.warn("Rejected {} {} - invalid token (neither local nor Keycloak accepted it): {}",
                            request.getMethod(), request.getURI(), e.getMessage());
                    return reject(exchange, HttpStatus.UNAUTHORIZED);
                });
    }

    private Mono<Void> proceed(ServerWebExchange exchange, GatewayFilterChain chain,
                                org.springframework.http.server.reactive.ServerHttpRequest request, long start) {
        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    long duration = System.currentTimeMillis() - start;
                    log.info("<-- {} {} ({} ms)", request.getMethod(), request.getURI(), duration);
                }));
    }

    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1; // run early
    }
}
