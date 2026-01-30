package com.msa.shop.settlement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Settlement Service API").version("1.0").description("일별 매출(GET /settlements/daily)·월별 매출(GET /settlements/monthly). RabbitMQ 결제 완료 이벤트 구독"));
    }
}
