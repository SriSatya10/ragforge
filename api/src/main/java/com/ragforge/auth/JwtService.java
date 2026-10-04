package com.ragforge.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Handles JWT creation and validation.
 * Tokens carry tenantId and email as claims — enough to reconstruct
 * the security principal without a DB call on every request.
 */
@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey signingKey;

    public JwtService(JwtProperties props) {
        this.props = props;
        // HMAC-SHA key derived from the secret string
        this.signingKey = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** Generate a JWT containing the tenant's id and email. */
    public String generateToken(Long tenantId, String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("tenantId", tenantId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + props.getExpirationMs()))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parse and validate the token. Returns claims if valid, null if invalid.
     * We return null instead of throwing so the filter can simply skip auth
     * and let Spring Security's 401 handler respond.
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
