package com.msa.shop.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * GET /api-docs 요청 시 통합 Swagger UI HTML 반환.
 * 각 서비스 OpenAPI는 /api-docs/user-service, /api-docs/product-service 등으로 프록시됨.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SwaggerUiFilter implements GlobalFilter {

    private static final String HTML = """
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>MSA Shop - API Docs</title>
  <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5.9.0/swagger-ui.css">
</head>
<body>
  <div id="swagger-ui"></div>
  <script src="https://unpkg.com/swagger-ui-dist@5.9.0/swagger-ui-bundle.js"></script>
  <script>
    window.onload = function() {
      window.ui = SwaggerUIBundle({
        urls: [
          { name: "User Service", url: "/api-docs/user-service" },
          { name: "Product Service", url: "/api-docs/product-service" },
          { name: "Order Service", url: "/api-docs/order-service" },
          { name: "Payment Service", url: "/api-docs/payment-service" },
          { name: "Settlement Service", url: "/api-docs/settlement-service" }
        ],
        dom_id: "#swagger-ui",
        presets: [
          SwaggerUIBundle.presets.apis,
          SwaggerUIBundle.SwaggerUIStandalonePreset
        ]
      });
    };
  </script>
</body>
</html>
""";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!"GET".equalsIgnoreCase(exchange.getRequest().getMethod().name())) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        if (!"/api-docs".equals(path)) {
            return chain.filter(exchange);
        }

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(org.springframework.http.HttpStatus.OK);
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE + "; charset=UTF-8");
        return response.writeWith(Mono.just(response.bufferFactory().wrap(HTML.getBytes(StandardCharsets.UTF_8))));
    }
}
