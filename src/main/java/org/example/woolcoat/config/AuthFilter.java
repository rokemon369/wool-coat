package org.example.woolcoat.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * API Key 鉴权过滤器：校验 Authorization: Bearer &lt;token&gt; 或 X-API-Key: &lt;token&gt;
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class AuthFilter extends OncePerRequestFilter {

    private final AuthProperties authProperties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!authProperties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        String expectKey = authProperties.getApiKey();
        if (expectKey == null || expectKey.isBlank()) {
            logger.warn("鉴权已开启但 AUTH_API_KEY 未配置，请求将放行，请尽快配置");
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (isExcluded(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);
        if (authProperties.getApiKey().equals(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"msg\":\"鉴权失败，请携带有效 Token（Authorization: Bearer <token> 或 X-API-Key: <token>）\"}");
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7).trim();
        }
        String apiKey = request.getHeader("X-API-Key");
        return apiKey != null ? apiKey.trim() : "";
    }

    private boolean isExcluded(String path) {
        List<String> excludes = authProperties.getExcludePaths();
        if (excludes == null) return false;
        for (String pattern : excludes) {
            if (pathMatcher.match(pattern, path)) return true;
        }
        return false;
    }
}
