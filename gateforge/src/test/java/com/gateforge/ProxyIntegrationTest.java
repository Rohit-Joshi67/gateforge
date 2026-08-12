package com.gateforge;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "gateforge.rate-limit.max-requests=100",
        "gateforge.rate-limit.window-seconds=60"
})
class ProxyIntegrationTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().port(9001))
            .build();

    @LocalServerPort
    private int port;

    private HttpClient httpClient;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newHttpClient();
        baseUrl = "http://localhost:" + port;
        wireMock.resetAll();
    }

    @Test
    void proxy_forwardsQueryStringAndHeaders() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/api/users/5"))
                .withQueryParam("role", equalTo("admin"))
                .withHeader("X-Request-Id", equalTo("abc-123"))
                .willReturn(okJson("{\"id\":5,\"role\":\"admin\"}")));

        String token = fetchToken();

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/users/5?role=admin"))
                        .GET()
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", "abc-123")
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"id\":5"));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/api/users/5"))
                .withQueryParam("role", equalTo("admin"))
                .withHeader("X-Request-Id", equalTo("abc-123")));
    }

    @Test
    void proxy_forwardsPostBody() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/api/users"))
                .withRequestBody(equalToJson("{\"name\":\"rahul\"}"))
                .willReturn(okJson("{\"id\":99,\"name\":\"rahul\"}")));

        String token = fetchToken();

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/users"))
                        .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"rahul\"}"))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"id\":99"));
        wireMock.verify(postRequestedFor(urlPathEqualTo("/api/users")));
    }

    @Test
    void proxy_unknownPath_returns404() throws Exception {
        String token = fetchToken();

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/api/unknown/1"))
                        .GET()
                        .header("Authorization", "Bearer " + token)
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
        assertEquals("No route found for path", response.body());
    }

    private String fetchToken() throws Exception {
        return httpClient.send(
                HttpRequest.newBuilder(URI.create(baseUrl + "/login?username=rahul")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }
}
