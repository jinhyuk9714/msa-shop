package com.msa.shop.payment.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * payment-service 통합 테스트.
 * - Testcontainers MySQL + RabbitMQ 사용. POST /payments, POST /payments/{id}/cancel 검증.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PaymentControllerIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8"))
            .withDatabaseName("paymentdb");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbitmq = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3-management"));

    final TestRestTemplate restTemplate = new TestRestTemplate();

    int port;

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    void setPort(int port) {
        this.port = port;
    }

    @Test
    @DisplayName("POST /payments → 200, success=true")
    void pay() {
        String base = "http://localhost:" + port;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                base + "/payments",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "userId", 1L,
                        "amount", 10000,
                        "paymentMethod", "CARD"
                ), headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().get("success")).isEqualTo(true);
        assertThat(res.getBody().get("paymentId")).isNotNull();
    }

    @Test
    @DisplayName("POST /payments (amount 0) → 400, success=false")
    void payInvalidAmount() {
        String base = "http://localhost:" + port;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                base + "/payments",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "userId", 1L,
                        "amount", 0,
                        "paymentMethod", "CARD"
                ), headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().get("success")).isEqualTo(false);
    }

    @Test
    @DisplayName("POST /payments → 200, POST /payments/{id}/cancel → 200")
    void payAndCancel() {
        String base = "http://localhost:" + port;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map<String, Object>> payRes = restTemplate.exchange(
                base + "/payments",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "userId", 1L,
                        "amount", 5000,
                        "paymentMethod", "CARD"
                ), headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        assertThat(payRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        Object paymentId = payRes.getBody().get("paymentId");
        assertThat(paymentId).isNotNull();

        ResponseEntity<Void> cancelRes = restTemplate.exchange(
                base + "/payments/" + paymentId + "/cancel",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                Void.class
        );
        assertThat(cancelRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
