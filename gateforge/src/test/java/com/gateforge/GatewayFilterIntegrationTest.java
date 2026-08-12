package com.gateforge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayFilterIntegrationTest {

    @LocalServerPort
    private int port;

    private HttpClient httpClient;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + port;
    }

    @Test
    void health_withoutAuth_returns200() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""));
        assertTrue(response.body().contains("\"service\":\"GateForge\""));
    }

    @Test
    void healthReady_withoutAuth_returns200() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/health/ready")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"backends\""));
    }

    @Test
    void login_withoutAuth_returnsJwt() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/login?username=rahul")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("."));
    }

    @Test
    void api_withoutAuth_returns401() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/users/5")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
        assertEquals("Missing or invalid Authorization header", response.body());
    }

    @Test
    void api_withInvalidToken_returns401() throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/users/5"))
                        .GET()
                        .header("Authorization", "Bearer invalid.token.here")
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(401, response.statusCode());
        assertEquals("Invalid or expired token", response.body());
    }

    @Test
    void api_withValidToken_unreachableBackend_returns504() throws Exception {
        String token = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/login?username=rahul")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/users/5"))
                        .GET()
                        .header("Authorization", "Bearer " + token)
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(504, response.statusCode());
        assertTrue(response.body().contains("user-service"));
    }
}
