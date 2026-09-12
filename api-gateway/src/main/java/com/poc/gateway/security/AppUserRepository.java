package com.poc.gateway.security;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface AppUserRepository extends ReactiveCrudRepository<AppUser, Long> {
    Mono<AppUser> findByUsername(String username);
}
