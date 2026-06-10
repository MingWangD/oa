package com.example.judicialappraisal.auth.service;

import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.auth.dto.CurrentUserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class JwtTokenService {

    private final SecretKey secretKey;
    private final String issuer;
    private final Duration expiration;

    public JwtTokenService(@Value("${app.jwt.secret}") String secret,
                           @Value("${app.jwt.issuer}") String issuer,
                           @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public String generateToken(CurrentUserInfo userInfo) {
        Instant now = Instant.now();
        List<String> roles = userInfo.roles().stream().map(CurrentUserRole::code).toList();
        return Jwts.builder()
                .subject(userInfo.username())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .claim("uid", userInfo.id())
                .claim("realName", userInfo.realName())
                .claim("roles", roles)
                .claim("perms", userInfo.permissions())
                .signWith(secretKey)
                .compact();
    }

    public CurrentUserInfo parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Number userId = claims.get("uid", Number.class);
        String username = claims.getSubject();
        String realName = claims.get("realName", String.class);
        if (!issuer.equals(claims.getIssuer()) || userId == null || !StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Invalid token claims");
        }

        List<String> roleCodes = castList(claims.get("roles"), String.class);
        List<CurrentUserRole> roles = roleCodes.stream()
                .map(code -> new CurrentUserRole(code, code))
                .toList();
        Set<String> permissions = castSet(claims.get("perms"), String.class);

        return new CurrentUserInfo(userId.longValue(), username, realName, null, null, null, null, null, null, null, roles, permissions);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castList(Object obj, Class<T> clazz) {
        if (obj instanceof List) {
            return (List<T>) obj;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private <T> Set<T> castSet(Object obj, Class<T> clazz) {
        if (obj instanceof Collection) {
            return ((Collection<Object>) obj).stream()
                    .map(o -> (T) o)
                    .collect(Collectors.toSet());
        }
        return Set.of();
    }
}
