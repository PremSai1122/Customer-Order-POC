package com.poc.gateway.security;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Issues the gateway's own tokens through Spring Security's JwtEncoder, so
 * signing and verification go through the same JOSE stack and the same
 * symmetric key wired in SecurityConfig.
 */
@Component
public class JwtUtil {

    private final JwtEncoder jwtEncoder;
    private final long expirationMs;

    public JwtUtil(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.expirationMs = properties.getExpirationMs();
    }

    public String generateToken(String username) {
        Instant now = Instant.now();
        // Set the algorithm explicitly rather than letting the encoder infer
        // one from the key, so the issued header always says HS256 - which is
        // exactly what the decoder in SecurityConfig is pinned to accept.
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plusMillis(expirationMs))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
