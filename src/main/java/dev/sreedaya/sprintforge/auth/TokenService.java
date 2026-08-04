package dev.sreedaya.sprintforge.auth;

import dev.sreedaya.sprintforge.common.ApiExceptionHandler.UnauthorizedException;
import dev.sreedaya.sprintforge.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokens;
    private final JwtEncoder jwtEncoder;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public TokenService(
            RefreshTokenRepository refreshTokens,
            JwtEncoder jwtEncoder,
            @Value("${app.jwt.ttl}") Duration accessTokenTtl,
            @Value("${app.jwt.refresh-ttl}") Duration refreshTokenTtl) {
        this.refreshTokens = refreshTokens;
        this.jwtEncoder = jwtEncoder;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public TokenPair issue(User user) {
        return new TokenPair(accessToken(user), createRefreshToken(user), accessTokenTtl.toSeconds());
    }

    @Transactional
    public RotatedToken rotate(String rawToken) {
        Instant now = Instant.now();
        RefreshToken current = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Refresh token is invalid"));
        if (current.getRevokedAt() != null || !current.getExpiresAt().isAfter(now)) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        String nextRawToken = newRawToken();
        String nextHash = hash(nextRawToken);
        current.setRevokedAt(now);
        current.setReplacedByHash(nextHash);

        RefreshToken replacement = newRefreshToken(current.getUser(), nextHash, now);
        refreshTokens.save(replacement);
        return new RotatedToken(
                current.getUser(),
                accessToken(current.getUser()),
                nextRawToken,
                accessTokenTtl.toSeconds());
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(Instant.now());
            }
        });
    }

    private String accessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("sprintforge")
                .issuedAt(now)
                .expiresAt(now.plus(accessTokenTtl))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String createRefreshToken(User user) {
        String rawToken = newRawToken();
        refreshTokens.save(newRefreshToken(user, hash(rawToken), Instant.now()));
        return rawToken;
    }

    private RefreshToken newRefreshToken(User user, String tokenHash, Instant now) {
        RefreshToken token = new RefreshToken();
        token.setId(UUID.randomUUID());
        token.setUser(user);
        token.setTokenHash(tokenHash);
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(refreshTokenTtl));
        return token;
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresIn) {}

    public record RotatedToken(
            User user, String accessToken, String refreshToken, long expiresIn) {}
}
