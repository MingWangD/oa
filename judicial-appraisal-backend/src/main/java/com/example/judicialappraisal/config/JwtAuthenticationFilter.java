package com.example.judicialappraisal.config;

import com.example.judicialappraisal.auth.dto.CurrentUserInfo;
import com.example.judicialappraisal.auth.service.JwtTokenService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService,
                                   RestAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtTokenService = jwtTokenService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean publicPath = isPublicPath(request.getServletPath());
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7).trim();
        if (!StringUtils.hasText(token)) {
            if (publicPath) {
                SecurityContextHolder.clearContext();
                filterChain.doFilter(request, response);
                return;
            }
            authenticationEntryPoint.commence(request, response, new BadCredentialsException("Invalid token"));
            return;
        }

        try {
            Authentication currentAuthentication = SecurityContextHolder.getContext().getAuthentication();
            if (currentAuthentication == null) {
                CurrentUserInfo userInfo = jwtTokenService.parseToken(token);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userInfo, token, Collections.emptyList());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            }
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            if (publicPath) {
                filterChain.doFilter(request, response);
                return;
            }
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new BadCredentialsException("Invalid or expired token", ex)
            );
        }
    }

    private boolean isPublicPath(String servletPath) {
        return "/api/auth/login".equals(servletPath)
                || "/api/auth/logout".equals(servletPath)
                || servletPath.startsWith("/swagger-ui/")
                || "/swagger-ui.html".equals(servletPath)
                || servletPath.startsWith("/v3/api-docs/");
    }
}
