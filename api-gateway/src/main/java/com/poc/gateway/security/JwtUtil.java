package com.poc.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JwtUtil {

    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final Pattern ALG = Pattern.compile("\"alg\"\\s*:\\s*\"([^\"]+)\"");

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                    @Value("${jwt.expiration-minutes}") long expirationMinutes) {
        // Explicit UTF-8 rather than the platform default charset, so the same
        // secret always derives the same key regardless of the machine's locale
        // settings - otherwise tokens issued on one host can fail to verify on
        // another.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }

    public String generateToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public Claims validateAndGetClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Cheap unauthenticated peek at the token header's "alg", used only to pick
     * which validator to hand the token to. Previously the filter ran the local
     * HMAC verification on every request and used the resulting JwtException as
     * a signal to fall through to Keycloak - so every OIDC request paid for
     * building an exception (stack trace included) on its normal, successful
     * path.
     *
     * <p>This decides nothing about trust. A forged header only changes which
     * validator rejects the token: an HMAC header still has to pass the local
     * signature check, and anything else still has to pass Keycloak's JWKS
     * check. Neither validator is bypassed.
     */
    public boolean looksLocallyIssued(String token) {
        int firstDot = token.indexOf('.');
        if (firstDot <= 0) {
            return false;
        }
        try {
            String header = new String(URL_DECODER.decode(token.substring(0, firstDot)), StandardCharsets.UTF_8);
            Matcher matcher = ALG.matcher(header);
            // Any HMAC variant, not HS256 specifically: jjwt picks the strongest
            // algorithm the configured secret supports, so the current 60-byte
            // jwt.secret actually yields HS384, and a longer one would yield HS512.
            // Keycloak signs with RS256, so "starts with HS" cleanly separates the two.
            return matcher.find() && matcher.group(1).startsWith("HS");
        } catch (IllegalArgumentException e) {
            // Not valid base64url - neither validator will accept it either.
            return false;
        }
    }
}
