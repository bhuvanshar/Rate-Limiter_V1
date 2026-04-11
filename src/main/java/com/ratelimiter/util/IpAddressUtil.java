package com.ratelimiter.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Extracts the real client IP from the request, accounting for
 * reverse proxies and load balancers that set X-Forwarded-For.
 */
public final class IpAddressUtil {

    private IpAddressUtil() {}

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
    };

    /**
     * Returns the most likely real client IP address.
     * X-Forwarded-For may contain a chain: "client, proxy1, proxy2".
     * We take the first (leftmost) entry as the original client IP.
     */
    public static String extractClientIp(HttpServletRequest request) {
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                // X-Forwarded-For can be "client, proxy1, proxy2"
                String ip = value.split(",")[0].trim();
                if (!ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                    return ip;
                }
            }
        }
        return request.getRemoteAddr();
    }
}
