package com.ragforge.auth;

import com.ragforge.apikey.ApiKey;
import com.ragforge.apikey.ApiKeyRepository;
import com.ragforge.ratelimit.RateLimitException;
import com.ragforge.ratelimit.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Intercepts requests looking for the X-API-Key header.
 * If present, validates the key, applies rate limits, and sets the SecurityContext.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyRepository apiKeyRepo;
    private final RateLimiterService rateLimiterService;

    public ApiKeyAuthenticationFilter(ApiKeyRepository apiKeyRepo, RateLimiterService rateLimiterService) {
        this.apiKeyRepo = apiKeyRepo;
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String rawKey = request.getHeader("X-API-Key");

        if (rawKey != null && !rawKey.trim().isEmpty()) {
            String keyHash = sha256(rawKey);

            Optional<ApiKey> apiKeyOpt = apiKeyRepo.findByKeyHash(keyHash);

            if (apiKeyOpt.isPresent()) {
                ApiKey apiKey = apiKeyOpt.get();

                if (apiKey.isActive()) {
                    try {
                        // Apply rate limiting before letting the request through
                        rateLimiterService.consumeOrThrow(apiKey.getId(), apiKey.getRateLimitPerMin());

                        // Build principal. email is set to null because it's a programmatic access.
                        TenantPrincipal principal = new TenantPrincipal(
                                apiKey.getTenant().getId(), 
                                null, 
                                apiKey.getId()
                        );

                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(principal, null, List.of());

                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } catch (RateLimitException e) {
                        // We must handle the exception here because filters run before the DispatcherServlet
                        // (which is where GlobalExceptionHandler works).
                        response.setStatus(429);
                        response.setHeader("Retry-After", String.valueOf(e.getRetryAfterSeconds()));
                        response.setContentType("application/json");
                        response.getWriter().write("""
                            {
                                "status": 429,
                                "error": "Rate limit exceeded"
                            }
                            """);
                        return; // Stop the filter chain
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
