package com.msa.shop.user.api;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * user-service 통합 테스트.
 * - Testcontainers MySQL 사용. 회원가입·로그인·GET /users/me 검증.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserControllerIntegrationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8"))
            .withDatabaseName("userdb");

    final TestRestTemplate restTemplate = new TestRestTemplate();

    int port;

    @org.springframework.beans.factory.annotation.Value("${local.server.port}")
    void setPort(int port) {
        this.port = port;
    }

    @Test
    @DisplayName("POST /users → 201, POST /auth/login → 200, GET /users/me → 200")
    void registerLoginAndGetMe() {
        String base = "http://localhost:" + port;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map<String, Object>> registerRes = restTemplate.exchange(
                base + "/users",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "email", "it@test.com",
                        "password", "pass123",
                        "name", "통합테스트유저"
                ), headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        assertThat(registerRes.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registerRes.getBody()).isNotNull();
        assertThat(registerRes.getBody().get("email")).isEqualTo("it@test.com");

        ResponseEntity<Map<String, Object>> loginRes = restTemplate.exchange(
                base + "/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("email", "it@test.com", "password", "pass123"), headers),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        assertThat(loginRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginRes.getBody()).isNotNull();
        String token = (String) loginRes.getBody().get("accessToken");
        assertThat(token).isNotBlank();

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.set("Authorization", "Bearer " + token);
        ResponseEntity<Map<String, Object>> meRes = restTemplate.exchange(
                base + "/users/me",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders),
                new ParameterizedTypeReference<Map<String, Object>>() {}
        );
        assertThat(meRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meRes.getBody()).isNotNull();
        assertThat(meRes.getBody().get("email")).isEqualTo("it@test.com");
    }
}
