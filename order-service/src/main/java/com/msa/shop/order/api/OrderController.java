package com.msa.shop.order.api;

import com.msa.shop.order.application.OrderService;
import com.msa.shop.order.domain.Order;
import com.msa.shop.order.domain.OrderStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

record CreateOrderRequest(Long productId, int quantity, String paymentMethod) {}

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

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<?> createOrder(
            @RequestHeader("Authorization") String authorization,
            @RequestBody CreateOrderRequest request
    ) {
        Long userId = extractUserIdFromToken(authorization);

        try {
            Order order = orderService.createOrder(
                    userId,
                    request.productId(),
                    request.quantity(),
                    request.paymentMethod()
            );
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(OrderResponse.from(order));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        Order order = orderService.getOrder(id);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    private Long extractUserIdFromToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Authorization 헤더가 올바르지 않습니다.");
        }
        String token = authorizationHeader.substring("Bearer ".length());
        // user-service 에서 발급하는 더미 토큰 규칙: dummy-token-for-user-{id}
        String prefix = "dummy-token-for-user-";
        if (!token.startsWith(prefix)) {
            throw new IllegalArgumentException("토큰 형식이 올바르지 않습니다.");
        }
        String idPart = token.substring(prefix.length());
        return Long.parseLong(idPart);
    }
}

