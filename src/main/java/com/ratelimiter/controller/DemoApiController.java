package com.ratelimiter.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Sample API endpoints to demonstrate and test rate limiting.
 * These simulate real API endpoints that would exist in your application.
 */
@RestController
@RequestMapping("/api")
public class DemoApiController {

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> getOrders() {
        return ResponseEntity.ok(Map.of(
                "endpoint", "GET /api/orders",
                "data", "Sample order list",
                "timestamp", Instant.now()
        ));
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of(
                "endpoint", "POST /api/orders",
                "message", "Order created successfully",
                "timestamp", Instant.now()
        ));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "endpoint", "GET /api/users/" + id,
                "data", Map.of("id", id, "name", "Demo User"),
                "timestamp", Instant.now()
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of(
                "endpoint", "POST /api/login",
                "message", "Login successful",
                "token", "demo-jwt-token",
                "timestamp", Instant.now()
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", Instant.now()
        ));
    }
}
