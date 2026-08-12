package com.gateforge.proxy;

import com.gateforge.routing.RouteConfig;
import com.gateforge.routing.RouteResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Optional;
import java.util.Set;

@RestController
public class ProxyController {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade",
            "host",
            "content-length"
    );

    private final RestClient restClient;
    private final RouteResolver routeResolver;

    public ProxyController(RestClient restClient, RouteResolver routeResolver) {
        this.restClient = restClient;
        this.routeResolver = routeResolver;
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        String queryString = request.getQueryString();

        Optional<RouteConfig> matchedRoute = routeResolver.resolve(path);

        if (matchedRoute.isEmpty()) {
            return ResponseEntity.status(404).body("No route found for path".getBytes());
        }

        RouteConfig route = matchedRoute.get();
        String targetUrl = route.getTargetUrl() + path;
        if (queryString != null && !queryString.isEmpty()) {
            targetUrl += "?" + queryString;
        }

        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        byte[] body = request.getInputStream().readAllBytes();

        RestClient.RequestBodySpec requestSpec = restClient.method(method).uri(targetUrl);
        copyRequestHeaders(request, requestSpec);

        try {
            ResponseEntity<byte[]> response = body.length > 0
                    ? requestSpec.body(body).retrieve().toEntity(byte[].class)
                    : requestSpec.retrieve().toEntity(byte[].class);

            return ResponseEntity
                    .status(response.getStatusCode())
                    .headers(headers -> headers.addAll(response.getHeaders()))
                    .body(response.getBody());

        } catch (ResourceAccessException e) {
            return ResponseEntity.status(504)
                    .body(("Backend service timed out or unreachable: " + route.getId()).getBytes());
        }
    }

    private void copyRequestHeaders(HttpServletRequest request, RestClient.RequestBodySpec requestSpec) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (isHopByHopHeader(name)) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                requestSpec.header(name, values.nextElement());
            }
        }
    }

    private boolean isHopByHopHeader(String headerName) {
        return HOP_BY_HOP_HEADERS.contains(headerName.toLowerCase());
    }
}
