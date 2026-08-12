package com.gateforge.health;

import com.gateforge.routing.GatewayProperties;
import com.gateforge.routing.RouteConfig;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class HealthController {

    private final RestClient restClient;
    private final GatewayProperties gatewayProperties;

    public HealthController(RestClient restClient, GatewayProperties gatewayProperties) {
        this.restClient = restClient;
        this.gatewayProperties = gatewayProperties;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "GateForge",
                "timestamp", Instant.now().toString()
        );
    }

    @GetMapping("/health/ready")
    public Map<String, Object> ready() {
        List<Map<String, String>> backends = gatewayProperties.getRoutes().stream()
                .map(this::checkBackend)
                .toList();

        boolean allUp = backends.stream().allMatch(b -> "UP".equals(b.get("status")));
        String overallStatus = allUp ? "UP" : "DEGRADED";

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", overallStatus);
        response.put("service", "GateForge");
        response.put("timestamp", Instant.now().toString());
        response.put("backends", backends);
        return response;
    }

    private Map<String, String> checkBackend(RouteConfig route) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("id", route.getId());
        result.put("target", route.getTargetUrl());

        try {
            restClient.method(HttpMethod.HEAD)
                    .uri(route.getTargetUrl())
                    .retrieve()
                    .toBodilessEntity();
            result.put("status", "UP");
        } catch (ResourceAccessException e) {
            result.put("status", "DOWN");
            result.put("reason", "unreachable or timed out");
        } catch (Exception e) {
            result.put("status", "UP");
        }

        return result;
    }
}
