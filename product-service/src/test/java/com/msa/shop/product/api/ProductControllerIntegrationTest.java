package com.msa.shop.product.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles("local")  // Redis 제외, 인메모리 캐시 사용
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
        assertThat(res.getBody().get(0)).containsKeys("id", "name", "category", "price", "stockQuantity");
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
        assertThat(res.getBody()).containsKeys("name", "category", "price", "stockQuantity");
    }

    @Test
    @DisplayName("GET /products?name=상품 → 200, 이름 부분 일치 검색")
    void getProductsByNameSearch() {
        String base = "http://localhost:" + port;
        ResponseEntity<List<Map<String, Object>>> res = restTemplate.exchange(
                base + "/products?name=상품",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody()).isNotEmpty();
        assertThat(res.getBody()).allMatch(m -> ((String) m.get("name")).contains("상품"));
    }

    @Test
    @DisplayName("GET /products?minPrice=5000&maxPrice=15000 → 200, 가격 범위 검색")
    void getProductsByPriceRange() {
        String base = "http://localhost:" + port;
        ResponseEntity<List<Map<String, Object>>> res = restTemplate.exchange(
                base + "/products?minPrice=5000&maxPrice=15000",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody()).isNotEmpty();
        assertThat(res.getBody()).allMatch(m -> {
            int price = (Integer) m.get("price");
            return price >= 5000 && price <= 15000;
        });
    }

    @Test
    @DisplayName("GET /products?category=전자 → 200, 카테고리 일치 검색")
    void getProductsByCategory() {
        String base = "http://localhost:" + port;
        ResponseEntity<List<Map<String, Object>>> res = restTemplate.exchange(
                base + "/products?category=전자",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody()).isNotEmpty();
        assertThat(res.getBody()).allMatch(m -> "전자".equals(m.get("category")));
    }

    @Test
    @DisplayName("GET /products?name=없는상품 → 200, 빈 목록")
    void getProductsByNameNoMatch() {
        String base = "http://localhost:" + port;
        ResponseEntity<List<Map<String, Object>>> res = restTemplate.exchange(
                base + "/products?name=없는상품",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() {}
        );
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody()).isEmpty();
    }
}
