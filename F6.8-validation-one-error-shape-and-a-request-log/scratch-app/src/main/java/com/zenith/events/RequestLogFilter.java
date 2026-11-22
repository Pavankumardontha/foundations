package com.zenith.events;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// One log line per request: method, path, status, duration. The seed of an access log.
@Component
@Order(1)   // outermost of our filters: logs even requests another filter kills
class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        try {
            chain.doFilter(request, response);   // everything else happens inside this call
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.info("{} {} -> {} ({} ms)",
                    request.getMethod(), request.getRequestURI(),
                    response.getStatus(), durationMs);
        }
    }
}
