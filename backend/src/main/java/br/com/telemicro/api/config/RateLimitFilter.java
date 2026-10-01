package br.com.telemicro.api.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

public class RateLimitFilter extends OncePerRequestFilter {
    private final RequestRateLimiter limiter;
    private final SecurityProperties properties;
    public RateLimitFilter(RequestRateLimiter limiter, SecurityProperties properties) {
        this.limiter = limiter;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("POST".equals(request.getMethod()) &&
                (path.equals("/api/v1/auth/login") || path.equals("/api/v1/budgets"))) {
            boolean login = path.endsWith("/login");
            long retry = limiter.retryAfter((login ? "login:" : "budget:") + request.getRemoteAddr(),
                    login ? properties.loginLimit() : properties.submissionLimit());
            if (retry > 0) {
                response.setHeader("Retry-After", Long.toString(retry));
                SecurityProblem.write(response, HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Aguarde e tente novamente.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
