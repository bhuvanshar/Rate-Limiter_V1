package com.ratelimiter.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.core.RequestContext;
import com.ratelimiter.core.ThrottleDecision;
import com.ratelimiter.dto.ThrottleResponse;
import com.ratelimiter.service.RateLimitService;
import com.ratelimiter.util.IpAddressUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Servlet filter that intercepts every request for rate limit evaluation.
 *
 * Why OncePerRequestFilter:
 * - Guarantees the filter runs exactly once per request, even if the
 *   request is forwarded internally (e.g., error dispatch).
 * - Runs before DispatcherServlet — rejected requests never reach
 *   Spring MVC, saving argument resolution and serialization overhead.
 *
 * Response headers (standard draft: RFC 6585 + draft-ietf-httpapi-ratelimit-headers):
 * - X-RateLimit-Limit: the configured maximum
 * - X-RateLimit-Remaining: tokens/requests left in current window
 * - X-RateLimit-Reset: epoch seconds when the limit resets
 * - Retry-After: seconds to wait before retrying (only on 429)
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String userIdHeader;
    private final List<String> excludedPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public RateLimitFilter(
            RateLimitService rateLimitService,
            ObjectMapper objectMapper,
            @Value("${rate-limiter.enabled:true}") boolean enabled,
            @Value("${rate-limiter.user-id-header:X-User-Id}") String userIdHeader,
            @Value("${rate-limiter.excluded-paths:}") List<String> excludedPaths) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.userIdHeader = userIdHeader;
        this.excludedPaths = excludedPaths;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) return true;

        String path = request.getRequestURI();
        return excludedPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        RequestContext context = buildContext(request);
        ThrottleDecision decision = rateLimitService.evaluate(context);

        // Always set rate limit headers (even for allowed requests)
        setRateLimitHeaders(response, decision);

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
        } else {
            writeThrottledResponse(response, decision);
        }
    }

    private RequestContext buildContext(HttpServletRequest request) {
        String userId = request.getHeader(userIdHeader);
        String ip = IpAddressUtil.extractClientIp(request);
        String method = request.getMethod();
        String path = request.getRequestURI();
        return new RequestContext(userId, ip, method, path);
    }

    private void setRateLimitHeaders(HttpServletResponse response, ThrottleDecision decision) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, decision.remaining())));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetAtEpochMs() / 1000));

        if (!decision.allowed() && decision.retryAfterMs() > 0) {
            // Retry-After in seconds (ceiling to avoid 0)
            long retryAfterSeconds = (long) Math.ceil(decision.retryAfterMs() / 1000.0);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        }
    }

    private void writeThrottledResponse(HttpServletResponse response, ThrottleDecision decision) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ThrottleResponse body = ThrottleResponse.of(
                "Rate limit exceeded. Please retry after " + decision.retryAfterMs() + "ms.",
                decision.retryAfterMs(),
                decision.ruleKey(),
                decision.limit(),
                decision.remaining()
        );

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
