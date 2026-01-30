package com.msa.shop.gateway.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인메모리 Rate Limiter. IP당 분당 요청 수 제한.
 * 초과 시 429 Too Many Requests. health 등 일부 경로는 제외 가능.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter implements GlobalFilter {

    private final int limitPerMinute;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private static final long WINDOW_MS = 60_000L;

    public RateLimitFilter(
            @Value("${app.rate-limit.per-minute:120}") int limitPerMinute
    ) {
        this.limitPerMinute = limitPerMinute;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (limitPerMinute <= 0) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        if ("/actuator/health".equals(path) || "/actuator/info".equals(path)) {
            return chain.filter(exchange);
        }

        String key = clientKey(exchange);
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, old) -> {
            if (old == null || now - old.startMs > WINDOW_MS) {
                return new Window(now, 1);
            }
            return new Window(old.startMs, old.count + 1);
        });

        if (w.count > limitPerMinute) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            String body = "{\"error\":\"TOO_MANY_REQUESTS\",\"message\":\"Rate limit exceeded. Try again later.\"}";
            return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
        }

        return chain.filter(exchange);
    }

    private String clientKey(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].strip();
        }
        var addr = exchange.getRequest().getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }

    private record Window(long startMs, int count) {}
}
