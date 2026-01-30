package com.msa.shop.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MSA Shop API Gateway")
                        .version("1.0")
                        .description("""
                                단일 진입점. 아래 경로로 요청 시 각 백엔드 서비스로 프록시됩니다.
                                
                                | 경로 | 서비스 | 포트(직접 접근 시) |
                                |------|--------|---------------------|
                                | /users/**, /auth/** | user-service | 8081 |
                                | /products/** | product-service | 8082 |
                                | /orders/** | order-service | 8083 |
                                | (결제는 order-service 내부 호출) | payment-service | 8084 |
                                | /settlements/** | settlement-service | 8085 |
                                
                                **각 서비스 Swagger UI**: 직접 접근 시 `http://localhost:{포트}/swagger-ui.html`
                                """));
    }
}
