package com.poc.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * The "pre filtering / post filtering" hook the transcript calls out: logs a
 * line on the way in and a line with the elapsed time on the way out.
 *
 * <p>Authentication is no longer done here. Spring Security's reactive filter
 * chain (see SecurityConfig) validates the bearer token and rejects anonymous
 * calls, and it runs as a WebFilter - ahead of gateway routing - so a request
 * that fails authentication is answered with a 401 before it ever reaches this
 * filter. What reaches here is therefore an already-authenticated request that
 * matched a route, which is exactly what the timing line is meant to measure.
 */
@Component
public class LoggingAndAuthFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingAndAuthFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        long start = System.currentTimeMillis();

        log.info("--> {} {}", request.getMethod(), request.getURI());

        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {
                    long duration = System.currentTimeMillis() - start;
                    log.info("<-- {} {} ({} ms)", request.getMethod(), request.getURI(), duration);
                }));
    }

    @Override
    public int getOrder() {
        return -1; // run early
    }
}
