package com.msa.shop.product.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * product-service 통합 테스트.
 * - Testcontainers MySQL 사용. ProductDataLoader로 시딩된 상품 조회 검증.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ProductControllerIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8"))
            .withDatabaseName("productdb");

    final TestRestTemplate restTemplate = new TestRestTemplate();

    int port;

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    void setPort(int port) {
        this.port = port;
    }

    @Test
    @DisplayName("GET /products → 200, 시딩된 상품 목록")
    void getProducts() {
        String base = "http://localhost:" + port;
        ResponseEntity<List<Map<String, Object>>> res = restTemplate.exchange(
                base + "/products",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody()).isNotEmpty();
        assertThat(res.getBody().get(0)).containsKeys("id", "name", "price", "stockQuantity");
    }

    @Test
    @DisplayName("GET /products/{id} → 200, 상품 상세")
    void getProduct() {
        String base = "http://localhost:" + port;
        ResponseEntity<Map<String, Object>> res = restTemplate.exchange(
                base + "/products/1",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().get("id")).isEqualTo(1);
        assertThat(res.getBody()).containsKeys("name", "price", "stockQuantity");
    }
}
