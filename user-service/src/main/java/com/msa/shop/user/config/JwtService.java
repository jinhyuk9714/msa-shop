package com.msa.shop.user.config;

import com.msa.shop.user.application.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 발급(로그인) 및 검증(GET /users/me).
 * HS256 사용. order-service에서 동일 secret으로 검증·userId 추출.
 */
@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms:3600000}") long expirationMs
    ) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("app.jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** 로그인 성공 시 액세스 토큰 발급. sub=email, userId 클레임 포함. */
    public String generateToken(Long userId, String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /** Bearer 토큰에서 userId 추출. 서명·만료 검증 후 반환. 실패 시 InvalidTokenException. */
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
