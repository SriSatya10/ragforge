package com.ragforge.auth;

import com.ragforge.auth.dto.AuthResponse;
import com.ragforge.auth.dto.LoginRequest;
import com.ragforge.auth.dto.RegisterRequest;
import com.ragforge.tenant.Tenant;
import com.ragforge.tenant.TenantRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Handles tenant registration and login.
 * Both endpoints are public (see SecurityConfig).
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final TenantRepository tenantRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(TenantRepository tenantRepo,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService) {
        this.tenantRepo = tenantRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        // Check for duplicate email and company name
        if (tenantRepo.existsByEmail(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        if (tenantRepo.existsByName(req.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Company name already taken");
        }

        Tenant tenant = new Tenant();
        tenant.setName(req.name());
        tenant.setEmail(req.email());
        tenant.setPasswordHash(passwordEncoder.encode(req.password()));

        tenant = tenantRepo.save(tenant);

        String token = jwtService.generateToken(tenant.getId(), tenant.getEmail());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token, tenant.getId(), tenant.getName(), tenant.getEmail()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        Tenant tenant = tenantRepo.findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        // Constant-time comparison via BCrypt — safe against timing attacks
        if (!passwordEncoder.matches(req.password(), tenant.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        String token = jwtService.generateToken(tenant.getId(), tenant.getEmail());

        return ResponseEntity.ok(
                new AuthResponse(token, tenant.getId(), tenant.getName(), tenant.getEmail()));
    }
}
