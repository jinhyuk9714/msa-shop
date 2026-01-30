package com.msa.shop.order.api;

import com.msa.shop.order.application.CartService;
import com.msa.shop.order.config.JwtSupport;
import com.msa.shop.order.domain.CartItem;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 장바구니 항목 추가 요청. */
record AddCartItemRequest(Long productId, int quantity) {}

/** 수량 변경 요청. */
record UpdateCartItemRequest(int quantity) {}

/** 장바구니 항목 응답. */
record CartItemResponse(Long productId, int quantity) {
    static CartItemResponse from(CartItem item) {
        return new CartItemResponse(item.getProductId(), item.getQuantity());
    }
}

/**
 * 장바구니 API. JWT 또는 X-User-Id 필수.
 */
@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;
    private final JwtSupport jwtSupport;

    public CartController(CartService cartService, JwtSupport jwtSupport) {
        this.cartService = cartService;
        this.jwtSupport = jwtSupport;
    }

    private Long resolveUserId(
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        if (xUserId != null && !xUserId.isBlank()) {
            try {
                return Long.parseLong(xUserId.trim());
            } catch (NumberFormatException e) {
                throw new com.msa.shop.order.application.InvalidTokenException("X-User-Id가 올바르지 않습니다.");
            }
        }
        return jwtSupport.parseUserIdFromBearer(authorization);
    }

    /** 장바구니 목록 조회. */
    @GetMapping
    public ResponseEntity<List<CartItemResponse>> getCart(
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = resolveUserId(xUserId, authorization);
        List<CartItem> items = cartService.getCart(userId);
        return ResponseEntity.ok(items.stream().map(CartItemResponse::from).toList());
    }

    /** 장바구니에 추가. 동일 상품이 있으면 수량 합산. 재고 부족 시 409. */
    @PostMapping("/items")
    public ResponseEntity<CartItemResponse> addItem(
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody AddCartItemRequest request
    ) {
        Long userId = resolveUserId(xUserId, authorization);
        CartItem item = cartService.addItem(userId, request.productId(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(CartItemResponse.from(item));
    }

    /** 수량 변경. quantity 0 이하면 삭제 후 204. */
    @PatchMapping("/items/{productId}")
    public ResponseEntity<CartItemResponse> updateQuantity(
            @PathVariable Long productId,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody UpdateCartItemRequest request
    ) {
        Long userId = resolveUserId(xUserId, authorization);
        CartItem item = cartService.updateQuantity(userId, productId, request.quantity());
        if (item == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(CartItemResponse.from(item));
    }

    /** 장바구니에서 항목 삭제. */
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeItem(
            @PathVariable Long productId,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = resolveUserId(xUserId, authorization);
        cartService.removeItem(userId, productId);
        return ResponseEntity.noContent().build();
    }

    /** 장바구니 비우기. */
    @DeleteMapping
    public ResponseEntity<Void> clearCart(
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = resolveUserId(xUserId, authorization);
        cartService.clearCart(userId);
        return ResponseEntity.noContent().build();
    }
}
