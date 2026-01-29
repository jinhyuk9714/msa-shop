package com.msa.shop.order.config;

import com.msa.shop.order.application.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * user-service가 발급한 JWT 검증 및 userId 추출.
 * user-service와 동일한 app.jwt.secret 사용 (HS256).
 */
@Component
public class JwtSupport {

    private final SecretKey secretKey;

    public JwtSupport(@Value("${app.jwt.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("app.jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Bearer 토큰 문자열에서 JWT 검증 후 userId 추출.
     * Authorization: Bearer {token} 형식이 아닌 경우 InvalidTokenException.
     */
    public Long parseUserIdFromBearer(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Authorization 헤더가 올바르지 않습니다.");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        return parseUserIdFromToken(token);
    }

    public Long parseUserIdFromToken(String token) {
        try {
            Claims payload = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Object userId = payload.get("userId");
            if (userId == null) {
                throw new InvalidTokenException("토큰에 userId가 없습니다.");
            }
            if (userId instanceof Integer) {
                return ((Integer) userId).longValue();
            }
            if (userId instanceof Long) {
                return (Long) userId;
            }
            throw new InvalidTokenException("토큰 형식이 올바르지 않습니다.");
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            throw new InvalidTokenException("토큰이 만료되었습니다.");
        } catch (io.jsonwebtoken.JwtException e) {
            throw new InvalidTokenException("토큰이 올바르지 않습니다.");
        }
    }
}
