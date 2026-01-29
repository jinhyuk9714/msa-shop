package com.msa.shop.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * order-service → product / payment 서비스 HTTP 호출용 RestTemplate Bean.
 * - RestTemplate: 동기 HTTP 클라이언트. GET/POST 등으로 REST API 호출.
 * - MSA에서 서비스 간 통신 시 자주 사용. (WebClient는 비동기.)
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
