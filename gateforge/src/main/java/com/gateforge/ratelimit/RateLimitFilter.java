package com.gateforge.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(3) // runs AFTER logging(1) and auth(2) — we rate-limit authenticated identities
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;

    public RateLimitFilter(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (request.getRequestURI().equals("/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Prefer authenticated username (set by JwtAuthFilter); fallback to IP
        String clientKey = (String) request.getAttribute("username");
        if (clientKey == null) {
            clientKey = request.getRemoteAddr();
        }

        if (!rateLimiter.isAllowed(clientKey)) {
            response.setStatus(429); // Too Many Requests
            response.getWriter().write("Rate limit exceeded. Try again later.");
            return;
        }

        filterChain.doFilter(request, response);
    }
}