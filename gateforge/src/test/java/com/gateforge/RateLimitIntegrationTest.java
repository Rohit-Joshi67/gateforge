package com.gateforge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "gateforge.rate-limit.max-requests=3",
        "gateforge.rate-limit.window-seconds=60"
})
class RateLimitIntegrationTest {

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
    void rateLimit_blocksAfterConfiguredMax() throws Exception {
        String token = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/login?username=rate-test-user")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();

        for (int i = 0; i < 3; i++) {
            HttpResponse<String> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/api/users/1"))
                            .GET()
                            .header("Authorization", "Bearer " + token)
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(504, response.statusCode(), "Request " + (i + 1) + " should reach proxy then timeout");
        }

        HttpResponse<String> blocked = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/users/1"))
                        .GET()
                        .header("Authorization", "Bearer " + token)
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(429, blocked.statusCode());
        assertEquals("Rate limit exceeded. Try again later.", blocked.body());
    }
}
