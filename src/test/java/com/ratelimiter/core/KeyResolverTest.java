package com.ratelimiter.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeyResolverTest {

    private KeyResolver keyResolver;

    @BeforeEach
    void setUp() {
        keyResolver = new KeyResolver();
    }

    @Test
    void shouldResolveUserKey() {
        RequestContext ctx = new RequestContext("42", "10.0.0.1", "GET", "/api/orders");
        String key = keyResolver.resolve(RateLimitScope.USER, ctx);
        assertEquals("user:42", key);
    }

    @Test
    void shouldResolveIpKey() {
        RequestContext ctx = new RequestContext(null, "10.0.0.1", "GET", "/api/orders");
        String key = keyResolver.resolve(RateLimitScope.IP, ctx);
        assertEquals("ip:10.0.0.1", key);
    }

    @Test
    void shouldResolveApiKey() {
        RequestContext ctx = new RequestContext(null, "10.0.0.1", "GET", "/api/orders");
        String key = keyResolver.resolve(RateLimitScope.API, ctx);
        assertEquals("api:GET:/api/orders", key);
    }

    @Test
    void shouldResolveUserApiCompositeKey() {
        RequestContext ctx = new RequestContext("42", "10.0.0.1", "GET", "/api/orders");
        String key = keyResolver.resolve(RateLimitScope.USER_API, ctx);
        assertEquals("user:42|api:GET:/api/orders", key);
    }

    @Test
    void shouldResolveIpApiCompositeKey() {
        RequestContext ctx = new RequestContext(null, "10.0.0.1", "POST", "/api/login");
        String key = keyResolver.resolve(RateLimitScope.IP_API, ctx);
        assertEquals("ip:10.0.0.1|api:POST:/api/login", key);
    }

    @Test
    void shouldResolveUserIpCompositeKey() {
        RequestContext ctx = new RequestContext("42", "10.0.0.1", "GET", "/api/orders");
        String key = keyResolver.resolve(RateLimitScope.USER_IP, ctx);
        assertEquals("user:42|ip:10.0.0.1", key);
    }

    @Test
    void shouldResolveFullCompositeKey() {
        RequestContext ctx = new RequestContext("42", "10.0.0.1", "POST", "/api/checkout");
        String key = keyResolver.resolve(RateLimitScope.USER_IP_API, ctx);
        assertEquals("user:42|ip:10.0.0.1|api:POST:/api/checkout", key);
    }

    @Test
    void shouldReturnNullForUserScopeWithoutUserId() {
        RequestContext ctx = new RequestContext(null, "10.0.0.1", "GET", "/api/orders");
        assertNull(keyResolver.resolve(RateLimitScope.USER, ctx));
    }

    @Test
    void shouldReturnNullForUserApiScopeWithoutUserId() {
        RequestContext ctx = new RequestContext(null, "10.0.0.1", "GET", "/api/orders");
        assertNull(keyResolver.resolve(RateLimitScope.USER_API, ctx));
    }

    @Test
    void shouldReturnNullForBlankUserId() {
        RequestContext ctx = new RequestContext("  ", "10.0.0.1", "GET", "/api/orders");
        assertNull(keyResolver.resolve(RateLimitScope.USER, ctx));
    }
}
