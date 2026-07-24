package com.gateforge.proxy;

import com.gateforge.routing.RouteConfig;
import com.gateforge.routing.RouteResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@RestController
public class ProxyController {

    private final RestClient restClient;
    private final RouteResolver routeResolver;

    public ProxyController(RestClient restClient, RouteResolver routeResolver) {
        this.restClient = restClient;
        this.routeResolver = routeResolver;
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        String path = request.getRequestURI();

        Optional<RouteConfig> matchedRoute = routeResolver.resolve(path);

        if (matchedRoute.isEmpty()) {
            return ResponseEntity.status(404).body("No route found for path".getBytes());
        }

        RouteConfig route = matchedRoute.get();
        String targetUrl = route.getTargetUrl() + path;

        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        ResponseEntity<byte[]> response = restClient
                .method(method)
                .uri(targetUrl)
                .retrieve()
                .toEntity(byte[].class);

        return ResponseEntity
                .status(response.getStatusCode())
                .headers(headers -> headers.addAll(response.getHeaders()))
                .body(response.getBody());
    }
}