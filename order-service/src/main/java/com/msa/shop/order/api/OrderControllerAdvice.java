package com.msa.shop.order.api;

import com.msa.shop.order.application.InsufficientStockException;
import com.msa.shop.order.application.InvalidTokenException;
import com.msa.shop.order.application.OrderNotFoundException;
import com.msa.shop.order.application.PaymentFailedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * order-service 전역 예외 처리.
 * - 주문 플로우 중 발생하는 예외 → HTTP 상태코드 + JSON { "error", "message" } 로 변환.
 * - REST API에서는 4xx/5xx와 body로 실패 이유를 명확히 전달하는 것이 관례.
 */
@RestControllerAdvice
public class OrderControllerAdvice {

    /** 재고 부족 → 409 CONFLICT */
    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<Map<String, String>> handleInsufficientStock(InsufficientStockException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", "CONFLICT", "message", ex.getMessage()));
    }

    /** 결제 실패 → 402 PAYMENT_REQUIRED */
    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<Map<String, String>> handlePaymentFailed(PaymentFailedException ex) {
        return ResponseEntity
                .status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of("error", "PAYMENT_REQUIRED", "message", ex.getMessage()));
    }

    /** 주문 없음 → 404 NOT_FOUND */
    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
    }

    /** 토큰 없음/형식 오류 → 401 UNAUTHORIZED */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, String>> handleInvalidToken(InvalidTokenException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "UNAUTHORIZED", "message", ex.getMessage()));
    }
}
