package com.ragforge.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Intercepts every request, extracts the JWT from the Authorization header,
 * validates it, and sets the SecurityContext so downstream code can call
 * SecurityContextHolder.getContext().getAuthentication() to get the tenant info.
 *
 * If the token is missing or invalid, the filter simply does not set auth —
 * Spring Security's default 401 handler takes over for protected endpoints.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // Only process Bearer tokens
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Claims claims = jwtService.parseToken(token);

            if (claims != null) {
                // Build a lightweight principal that carries tenantId
                Long tenantId = claims.get("tenantId", Long.class);
                String email = claims.getSubject();

                TenantPrincipal principal = new TenantPrincipal(tenantId, email);

                // Empty authorities for now — we have a single role (tenant)
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, List.of());

                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}
