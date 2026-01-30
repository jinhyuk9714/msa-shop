package com.msa.shop.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Payment Service API").version("1.0").description("결제 시도(POST /payments)·결제 취소(POST /payments/{id}/cancel). order-service 내부 호출용"));
    }
}
