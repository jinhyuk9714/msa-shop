package com.msa.shop.order.application;

import com.msa.shop.order.domain.Order;
import com.msa.shop.order.domain.OrderRepository;
import com.msa.shop.order.domain.OrderStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 주문 도메인 + 오케스트레이션.
 * - createOrder: product-service(가격·재고) → payment-service(결제) → 주문 저장.
 * - MSA에서는 서비스 간 REST 호출. 일부 실패 시 Retry/CircuitBreaker로 견고성 확보.
 */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;
    private final String productServiceBaseUrl;
    private final String paymentServiceBaseUrl;

    public OrderService(
            OrderRepository orderRepository,
            RestTemplate restTemplate,
            @Value("${product-service.base-url}") String productServiceBaseUrl,
            @Value("${payment-service.base-url}") String paymentServiceBaseUrl
    ) {
        this.orderRepository = orderRepository;
        this.restTemplate = restTemplate;
        this.productServiceBaseUrl = productServiceBaseUrl;
        this.paymentServiceBaseUrl = paymentServiceBaseUrl;
    }

    /**
     * 주문 생성 플로우 (동기 오케스트레이션).
     * 1. product-service에서 가격 조회 → totalAmount = price * quantity
     * 2. product-service 재고 예약 (실패 시 InsufficientStockException)
     * 3. payment-service 결제 요청 (실패 시 PaymentFailedException)
     * 4. 주문 저장 (PAID)
     * - 2단계에서는 SAGA/보상 트랜잭션으로 "재고 예약 후 결제 실패" 등 일관성 이슈 다룸.
     */
    @Transactional
    public Order createOrder(Long userId, Long productId, int quantity, String paymentMethod) {
        ProductResponse product = getProduct(productId);
        int totalAmount = product.price() * quantity;

        // 1) 재고 예약
        ReserveStockResponse stockResponse = reserveStock(userId, productId, quantity);
        if (!stockResponse.success()) {
            throw new InsufficientStockException("재고 부족: " + stockResponse.reason());
        }

        // 2) 결제 요청 + 실패 시 보상(SAGA 보상 트랜잭션: 재고 복구)
        try {
            PaymentResponse paymentResponse = requestPayment(userId, totalAmount, paymentMethod);
            if (!paymentResponse.success()) {
                // 결제 실패 → 재고 복구 시도 후 예외
                safelyReleaseStock(userId, productId, quantity);
                throw new PaymentFailedException("결제 실패: " + paymentResponse.reason());
            }
        } catch (RuntimeException ex) {
            // 네트워크 오류 등 requestPayment() 내부 예외도 재고 복구 대상
            safelyReleaseStock(userId, productId, quantity);
            throw ex;
        }

        Order order = new Order(userId, productId, quantity, totalAmount, OrderStatus.PAID);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException("주문을 찾을 수 없습니다. id=" + id));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByUser(Long userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * product-service GET /products/{id} 호출.
     * - @Retry: 실패 시 재시도 (application.yml maxAttempts, waitDuration).
     * - @CircuitBreaker: 연속 실패 시 회로 열어 과부하 방지 (slidingWindowSize, failureRateThreshold 등).
     */
    @Retry(name = "productService")
    @CircuitBreaker(name = "productService")
    ProductResponse getProduct(Long productId) {
        String url = productServiceBaseUrl + "/products/" + productId;
        ResponseEntity<ProductResponse> response =
                restTemplate.getForEntity(url, ProductResponse.class);
        return response.getBody();
    }

    /** product-service POST /internal/stocks/reserve. 재고 예약/차감. */
    @Retry(name = "productService")
    @CircuitBreaker(name = "productService")
    ReserveStockResponse reserveStock(Long userId, Long productId, int quantity) {
        String url = productServiceBaseUrl + "/internal/stocks/reserve";
        Map<String, Object> body = Map.of(
                "userId", userId,
                "productId", productId,
                "quantity", quantity
        );
        ResponseEntity<ReserveStockResponse> response =
                restTemplate.postForEntity(url, body, ReserveStockResponse.class);
        return response.getBody();
    }

    /**
     * product-service POST /internal/stocks/release.
     * - 재고 예약 후 결제 실패/오류 시 재고를 다시 복구하는 보상 트랜잭션.
     */
    @Retry(name = "productService")
    @CircuitBreaker(name = "productService")
    void releaseStock(Long userId, Long productId, int quantity) {
        String url = productServiceBaseUrl + "/internal/stocks/release";
        Map<String, Object> body = Map.of(
                "userId", userId,
                "productId", productId,
                "quantity", quantity
        );
        // 응답 body는 따로 사용하지 않으므로 성공 여부만 신뢰
        restTemplate.postForEntity(url, body, Void.class);
    }

    /**
     * 보상 트랜잭션 실행 시, 보상 자체가 실패하더라도 원래 예외를 숨기지 않기 위해
     * try/catch 로 감싼 안전한 래퍼 메서드.
     */
    private void safelyReleaseStock(Long userId, Long productId, int quantity) {
        try {
            releaseStock(userId, productId, quantity);
        } catch (Exception ignored) {
            // 로그를 붙이고 싶다면 여기에서 처리 (예: logger.warn(...))
        }
    }

    /** payment-service POST /payments. 결제 요청. */
    @Retry(name = "paymentService")
    @CircuitBreaker(name = "paymentService")
    PaymentResponse requestPayment(Long userId, int amount, String paymentMethod) {
        String url = paymentServiceBaseUrl + "/payments";
        Map<String, Object> body = Map.of(
                "userId", userId,
                "amount", amount,
                "paymentMethod", paymentMethod
        );
        ResponseEntity<PaymentResponse> response =
                restTemplate.postForEntity(url, body, PaymentResponse.class);
        return response.getBody();
    }

    /** product-service 응답 DTO (내부 전용). */
    public record ProductResponse(Long id, String name, int price, int stockQuantity) {}

    /** 재고 예약 API 응답. success=false면 "재고 부족" 등. */
    public record ReserveStockResponse(boolean success, String reason, int remainingStock) {}

    /** 결제 API 응답. success=false면 "결제 실패" 등. */
    public record PaymentResponse(boolean success, Long paymentId, String reason) {}
}
