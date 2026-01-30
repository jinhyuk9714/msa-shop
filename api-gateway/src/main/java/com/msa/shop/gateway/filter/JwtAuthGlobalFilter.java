package com.msa.shop.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * /orders/**, /users/me 요청 시 JWT 검증 후 X-User-Id 헤더로 downstream 전달.
 * user-service와 동일한 app.jwt.secret 사용.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class JwtAuthGlobalFilter implements GlobalFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String HEADER_X_USER_ID = "X-User-Id";

    private static final List<Pattern> AUTH_REQUIRED_PATHS = List.of(
            Pattern.compile("^/orders/.*"),
            Pattern.compile("^/users/me$")
    );

    private final SecretKey secretKey;

    public JwtAuthGlobalFilter(@Value("${app.jwt.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("app.jwt.secret must be at least 32 bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (!requiresAuth(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return respondUnauthorized(exchange.getResponse(), "Authorization 헤더가 올바르지 않습니다.");
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        Long userId;
        try {
            userId = parseUserId(token);
        } catch (Exception e) {
            return respondUnauthorized(exchange.getResponse(), "토큰이 올바르지 않습니다.");
        }

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .header(HEADER_X_USER_ID, String.valueOf(userId))
                .build();
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    private boolean requiresAuth(String path) {
        return AUTH_REQUIRED_PATHS.stream().anyMatch(p -> p.matcher(path).matches());
    }

    private Long parseUserId(String token) {
        Claims payload = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Object userId = payload.get("userId");
        if (userId == null) throw new IllegalArgumentException("userId missing");
        if (userId instanceof Integer) return ((Integer) userId).longValue();
        if (userId instanceof Long) return (Long) userId;
        throw new IllegalArgumentException("invalid userId");
    }

    private Mono<Void> respondUnauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"error\":\"UNAUTHORIZED\",\"message\":\"" + escapeJson(message) + "\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
