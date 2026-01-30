package com.msa.shop.order.api;

import com.msa.shop.order.application.CartService;
import com.msa.shop.order.application.OrderService;
import com.msa.shop.order.config.JwtSupport;
import com.msa.shop.order.domain.Order;
import com.msa.shop.order.domain.OrderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** POST /orders 요청 DTO. */
record CreateOrderRequest(Long productId, int quantity, String paymentMethod) {}

/** POST /orders/from-cart 요청 DTO. paymentMethod 생략 시 CARD. */
record FromCartRequest(String paymentMethod) {}

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
 * - API Gateway 경유 시 X-User-Id 헤더 사용, 직접 호출 시 Authorization: Bearer {JWT} 검증.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final CartService cartService;
    private final JwtSupport jwtSupport;

    public OrderController(OrderService orderService, CartService cartService, JwtSupport jwtSupport) {
        this.orderService = orderService;
        this.cartService = cartService;
        this.jwtSupport = jwtSupport;
    }

    /**
     * 주문 생성. product-service(가격·재고), payment-service(결제) 내부 호출 후 주문 저장.
     * - 재고 부족 → InsufficientStockException → 409
     * - 결제 실패 → PaymentFailedException → 402
     * - 토큰 오류 → InvalidTokenException → 401
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody CreateOrderRequest request
    ) {
        Long userId = resolveUserId(xUserId, authorization);
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
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = resolveUserId(xUserId, authorization);
        List<Order> orders = orderService.getOrdersByUser(userId);
        return ResponseEntity.ok(orders.stream().map(OrderResponse::from).toList());
    }

    /**
     * 장바구니 전체로 주문 생성. 품목별로 주문 생성 후 장바구니 비움.
     * /{id} 보다 위에 두어야 POST /orders/from-cart 가 GET /orders/{id} 에 걸리지 않음.
     */
    @PostMapping("/from-cart")
    public ResponseEntity<List<OrderResponse>> createOrdersFromCart(
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) FromCartRequest request
    ) {
        Long userId = resolveUserId(xUserId, authorization);
        String paymentMethod = request != null && request.paymentMethod() != null ? request.paymentMethod() : "CARD";
        List<Order> orders = cartService.createOrdersFromCart(userId, paymentMethod);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orders.stream().map(OrderResponse::from).toList());
    }

    /** Gateway 경유 시 X-User-Id, 직접 호출 시 Authorization Bearer JWT. */
    private Long resolveUserId(String xUserId, String authorization) {
        if (xUserId != null && !xUserId.isBlank()) {
            try {
                return Long.parseLong(xUserId.trim());
            } catch (NumberFormatException e) {
                throw new com.msa.shop.order.application.InvalidTokenException("X-User-Id가 올바르지 않습니다.");
            }
        }
        return jwtSupport.parseUserIdFromBearer(authorization);
    }

    /** 주문 단건 조회. 없으면 OrderNotFoundException → 404. {id}는 숫자만 허용해 /orders/from-cart가 여기 걸리지 않도록 함. */
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        Order order = orderService.getOrder(id);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    /**
     * 주문 취소. PAID 상태만 가능. 결제 취소 + 재고 복구 후 CANCELLED.
     * - 이미 취소됨/결제 정보 없음 → 409 CONFLICT
     */
    @PatchMapping("/{id:\\d+}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = resolveUserId(xUserId, authorization);
        Order order = orderService.cancelOrder(id, userId);
        return ResponseEntity.ok(OrderResponse.from(order));
    }
}
