package com.msa.shop.product.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Product Service API").version("1.0").description("상품 목록·검색(name, minPrice, maxPrice)·단건 조회(GET /products). 내부: POST /internal/stocks/reserve, /internal/stocks/release(재고 예약/복구)"));
    }
}
