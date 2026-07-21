package com.gateforge.proxy;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import jakarta.servlet.http.HttpServletRequest;

@RestController
public class ProxyController {

    private final RestClient restClient;
    private static final String BACKEND_BASE_URL = "http://localhost:9001";

    public ProxyController(RestClient restClient) {
        this.restClient = restClient;
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) {
        String path = request.getRequestURI();
        String targetUrl = BACKEND_BASE_URL + path;

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