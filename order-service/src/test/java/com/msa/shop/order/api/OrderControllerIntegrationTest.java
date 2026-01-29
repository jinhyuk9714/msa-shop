package com.msa.shop.order.api;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * order-service 통합 테스트.
 * - Testcontainers MySQL 사용. product/payment 는 MockWebServer 로 스텁.
 * - POST /orders (X-User-Id), GET /orders/{id} 검증.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderControllerIntegrationTest {

    static MockWebServer productServer;
    static MockWebServer paymentServer;

    static {
        productServer = new MockWebServer();
        paymentServer = new MockWebServer();
        try {
            productServer.start();
            paymentServer.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8"))
            .withDatabaseName("orderdb");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("product-service.base-url", () -> "http://localhost:" + productServer.getPort());
        registry.add("payment-service.base-url", () -> "http://localhost:" + paymentServer.getPort());
    }

    @BeforeAll
    static void setUpMockServers() {
        productServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("GET".equals(request.getMethod()) && request.getPath().startsWith("/products/")) {
                    return new MockResponse().setBody("{\"id\":1,\"name\":\"A\",\"price\":10000,\"stockQuantity\":10}")
                            .setHeader("Content-Type", "application/json");
                }
                if ("POST".equals(request.getMethod()) && "/internal/stocks/reserve".equals(request.getPath())) {
                    return new MockResponse().setBody("{\"success\":true,\"reason\":\"성공\",\"remainingStock\":8}")
                            .setHeader("Content-Type", "application/json");
                }
                if ("POST".equals(request.getMethod()) && "/internal/stocks/release".equals(request.getPath())) {
                    return new MockResponse().setResponseCode(200);
                }
                return new MockResponse().setResponseCode(404);
            }
        });
        paymentServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("POST".equals(request.getMethod()) && "/payments".equals(request.getPath())) {
                    return new MockResponse().setBody("{\"success\":true,\"paymentId\":1,\"reason\":\"APPROVED\"}")
                            .setHeader("Content-Type", "application/json");
                }
                return new MockResponse().setResponseCode(404);
            }
        });
    }

    @AfterAll
    static void tearDown() throws IOException {
        productServer.shutdown();
        paymentServer.shutdown();
    }

    final TestRestTemplate restTemplate = new TestRestTemplate();

    int port;

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    void setPort(int port) {
        this.port = port;
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("POST /orders (X-User-Id) → 201, GET /orders/{id} → 200")
    void createOrderAndGet() {
        String base = "http://localhost:" + port;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-User-Id", "1");

        ResponseEntity<Map<String, Object>> createRes = restTemplate.exchange(
                base + "/orders",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("productId", 1, "quantity", 2, "paymentMethod", "CARD"), headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        assertThat(createRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createRes.getBody()).isNotNull();
        assertThat(createRes.getBody().get("status")).isEqualTo("PAID");
        Object id = createRes.getBody().get("id");
        assertThat(id).isNotNull();

        ResponseEntity<Map<String, Object>> getRes = restTemplate.exchange(
                base + "/orders/" + id,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        assertThat(getRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getRes.getBody()).isNotNull();
        assertThat(getRes.getBody().get("id")).isEqualTo(id);
        assertThat(getRes.getBody().get("userId")).isEqualTo(1);
        assertThat(getRes.getBody().get("totalAmount")).isEqualTo(20000);
    }
}
