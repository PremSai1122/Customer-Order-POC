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
import org.springframework.http.server.reactive.ServerHttpRequest;
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
 * Keycloak-issued RS256 token (from a genuine OIDC login). The token's
 * own header says which kind it claims to be, so each request runs exactly
 * one signature check instead of always attempting the local one first and
 * treating its failure as "must be a Keycloak token".
 */
@Component
public class LoggingAndAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingAndAuthFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final OidcJwtValidator oidcJwtValidator;

    public LoggingAndAuthFilter(JwtUtil jwtUtil, OidcJwtValidator oidcJwtValidator) {
        this.jwtUtil = jwtUtil;
        this.oidcJwtValidator = oidcJwtValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long start = System.nanoTime();

        // Log the path, not the full URI - query strings routinely carry ids and
        // filter values that don't belong in an access log.
        log.info("--> {} {}", request.getMethod(), request.getPath());

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            log.warn("Rejected {} {} - missing/malformed Authorization header",
                    request.getMethod(), request.getPath());
            return reject(exchange, HttpStatus.UNAUTHORIZED);
        }
        String token = authHeader.substring(BEARER_PREFIX.length());

        if (jwtUtil.looksLocallyIssued(token)) {
            try {
                jwtUtil.validateAndGetClaims(token);
            } catch (JwtException e) {
                log.warn("Rejected {} {} - local token failed validation: {}",
                        request.getMethod(), request.getPath(), e.getMessage());
                return reject(exchange, HttpStatus.UNAUTHORIZED);
            }
            return proceed(exchange, chain, request, start);
        }

        return oidcJwtValidator.validate(token)
                .flatMap(jwt -> proceed(exchange, chain, request, start))
                .onErrorResume(e -> {
                    log.warn("Rejected {} {} - Keycloak rejected the token: {}",
                            request.getMethod(), request.getPath(), e.getMessage());
                    return reject(exchange, HttpStatus.UNAUTHORIZED);
                });
    }

    private Mono<Void> proceed(ServerWebExchange exchange, GatewayFilterChain chain,
                                ServerHttpRequest request, long start) {
        return chain.filter(exchange)
                .doFinally(signal -> {
                    // doFinally rather than then(...) so the response line is still
                    // logged when the client disconnects or the chain errors, and so
                    // the timing covers the whole exchange.
                    long millis = (System.nanoTime() - start) / 1_000_000;
                    log.info("<-- {} {} {} ({} ms)", request.getMethod(), request.getPath(),
                            exchange.getResponse().getStatusCode(), millis);
                });
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
