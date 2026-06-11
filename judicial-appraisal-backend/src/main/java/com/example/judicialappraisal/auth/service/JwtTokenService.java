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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        List<Map<String, Object>> roles = userInfo.roles().stream()
                .map(role -> {
                    Map<String, Object> claim = new LinkedHashMap<>();
                    claim.put("id", role.id());
                    claim.put("code", role.code());
                    claim.put("name", role.name());
                    claim.put("dataScope", role.dataScope());
                    claim.put("customDeptIds", role.customDeptIds());
                    return claim;
                })
                .toList();
        return Jwts.builder()
                .subject(userInfo.username())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .claim("uid", userInfo.id())
                .claim("realName", userInfo.realName())
                .claim("deptId", userInfo.deptId())
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
        Number deptId = claims.get("deptId", Number.class);
        String username = claims.getSubject();
        String realName = claims.get("realName", String.class);
        if (!issuer.equals(claims.getIssuer()) || userId == null || !StringUtils.hasText(username)) {
            throw new IllegalArgumentException("Invalid token claims");
        }

        List<CurrentUserRole> roles = castRoles(claims.get("roles"));
        Set<String> permissions = castSet(claims.get("perms"), String.class);

        return new CurrentUserInfo(
                userId.longValue(),
                username,
                realName,
                null,
                null,
                deptId == null ? null : deptId.longValue(),
                null,
                null,
                null,
                null,
                roles,
                permissions
        );
    }

    @SuppressWarnings("unchecked")
    private List<CurrentUserRole> castRoles(Object obj) {
        if (!(obj instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(role -> new CurrentUserRole(
                        longValue(role.get("id")),
                        stringValue(role.get("code")),
                        stringValue(role.get("name")),
                        stringValue(role.get("dataScope")),
                        longList(role.get("customDeptIds"))
                ))
                .filter(role -> StringUtils.hasText(role.code()))
                .toList();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        return Long.valueOf(String.valueOf(value));
    }

    private List<Long> longList(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(this::longValue)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
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
