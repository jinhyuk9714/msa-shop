package com.msa.shop.order.api;

import com.msa.shop.order.application.InvalidTokenException;
import com.msa.shop.order.application.OrderService;
import com.msa.shop.order.domain.Order;
import com.msa.shop.order.domain.OrderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** POST /orders 요청 DTO. */
record CreateOrderRequest(Long productId, int quantity, String paymentMethod) {}

/** 주문 API 응답 DTO. */
record OrderResponse(
        Long id,
        Long userId,
        Long productId,
        int quantity,
        int totalAmount,
        OrderStatus status
) {
    static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getProductId(),
                order.getQuantity(),
                order.getTotalAmount(),
                order.getStatus()
        );
    }
}

/**
 * order-service HTTP API 진입점.
 * - 주문 생성(POST /orders), 단건 조회(GET /orders/{id}), 내 주문 목록(GET /orders/me).
 * - Authorization: Bearer dummy-token-for-user-{id} 필수 (createOrder, getMyOrders).
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 주문 생성. product-service(가격·재고), payment-service(결제) 내부 호출 후 주문 저장.
     * - 재고 부족 → InsufficientStockException → 409
     * - 결제 실패 → PaymentFailedException → 402
     * - 토큰 오류 → InvalidTokenException → 401
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("Authorization") String authorization,
            @RequestBody CreateOrderRequest request
    ) {
        Long userId = extractUserIdFromToken(authorization);
        Order order = orderService.createOrder(
                userId,
                request.productId(),
                request.quantity(),
                request.paymentMethod()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(OrderResponse.from(order));
    }

    /** 내 주문 목록. createdAt 내림차순. */
    @GetMapping("/me")
    public ResponseEntity<List<OrderResponse>> getMyOrders(
            @RequestHeader("Authorization") String authorization
    ) {
        Long userId = extractUserIdFromToken(authorization);
        List<Order> orders = orderService.getOrdersByUser(userId);
        return ResponseEntity.ok(orders.stream().map(OrderResponse::from).toList());
    }

    /** 주문 단건 조회. 없으면 OrderNotFoundException → 404. */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        Order order = orderService.getOrder(id);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    /**
     * Authorization: Bearer dummy-token-for-user-{id} 에서 userId 추출.
     * user-service 로그인 응답 규칙과 동일하게 유지.
     */
    private Long extractUserIdFromToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Authorization 헤더가 올바르지 않습니다.");
        }
        String token = authorizationHeader.substring("Bearer ".length());
        String prefix = "dummy-token-for-user-";
        if (!token.startsWith(prefix)) {
            throw new InvalidTokenException("토큰 형식이 올바르지 않습니다.");
        }
        try {
            return Long.parseLong(token.substring(prefix.length()));
        } catch (NumberFormatException e) {
            throw new InvalidTokenException("토큰 형식이 올바르지 않습니다.");
        }
    }
}
