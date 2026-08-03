package dev.sreedaya.sprintforge.auth;

import dev.sreedaya.sprintforge.common.ApiExceptionHandler.ConflictException;
import dev.sreedaya.sprintforge.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserRepository users; private final PasswordEncoder passwords; private final JwtEncoder encoder; private final Duration ttl;
    public AuthController(UserRepository users, PasswordEncoder passwords, JwtEncoder encoder, @Value("${app.jwt.ttl}") Duration ttl) {
        this.users = users; this.passwords = passwords; this.encoder = encoder; this.ttl = ttl;
    }

    public record RegisterRequest(@NotBlank @Email String email, @NotBlank @Size(min=8,max=72) String password,
                                  @NotBlank @Size(max=120) String displayName) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record AuthResponse(String token, String tokenType, long expiresIn, UserView user) {}
    public record UserView(UUID id, String email, String displayName, String role) {}

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) throw new ConflictException("Email is already registered");
        var user = new User(); user.setId(UUID.randomUUID()); user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwords.encode(request.password())); user.setDisplayName(request.displayName().trim());
        user.setRole(User.Role.USER); user.setCreatedAt(Instant.now()); users.save(user); return token(user);
    }

    @PostMapping("/login")
    AuthResponse login(@Valid @RequestBody LoginRequest request) {
        var user = users.findByEmailIgnoreCase(request.email()).orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        if (!passwords.matches(request.password(), user.getPasswordHash())) throw new BadCredentialsException("Invalid credentials");
        return token(user);
    }

    private AuthResponse token(User user) {
        var now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer("sprintforge").issuedAt(now).expiresAt(now.plus(ttl))
                .subject(user.getId().toString()).claim("email", user.getEmail()).claim("scope", user.getRole().name()).build();
        var value = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResponse(value, "Bearer", ttl.toSeconds(), new UserView(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole().name()));
    }
}
