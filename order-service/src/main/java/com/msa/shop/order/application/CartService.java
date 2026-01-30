package com.msa.shop.order.application;

import com.msa.shop.order.domain.CartItem;
import com.msa.shop.order.domain.CartItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 장바구니 CRUD. 상품 추가 시 product-service로 존재·재고 검증.
 */
@Service
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final OrderService orderService;

    public CartService(CartItemRepository cartItemRepository, OrderService orderService) {
        this.cartItemRepository = cartItemRepository;
        this.orderService = orderService;
    }

    /**
     * 장바구니에 추가. 동일 상품이 있으면 수량 합산(재고 초과 시 InsufficientStockException).
     */
    @Transactional
    public CartItem addItem(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
        OrderService.ProductResponse product = orderService.getProduct(productId);
        if (product.stockQuantity() < quantity) {
            throw new InsufficientStockException("재고 부족: 현재 " + product.stockQuantity() + "개");
        }
        return cartItemRepository.findByUserIdAndProductId(userId, productId)
                .map(existing -> {
                    int newQty = existing.getQuantity() + quantity;
                    if (product.stockQuantity() < newQty) {
                        throw new InsufficientStockException("재고 부족: 최대 " + product.stockQuantity() + "개");
                    }
                    existing.setQuantity(newQty);
                    return cartItemRepository.save(existing);
                })
                .orElseGet(() -> cartItemRepository.save(new CartItem(userId, productId, quantity)));
    }

    /**
     * 수량 변경. 0 이하면 삭제.
     */
    @Transactional
    public CartItem updateQuantity(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            cartItemRepository.deleteByUserIdAndProductId(userId, productId);
            return null;
        }
        OrderService.ProductResponse product = orderService.getProduct(productId);
        if (product.stockQuantity() < quantity) {
            throw new InsufficientStockException("재고 부족: 최대 " + product.stockQuantity() + "개");
        }
        CartItem item = cartItemRepository.findByUserIdAndProductId(userId, productId)
                .orElseThrow(() -> new OrderNotFoundException("장바구니에 해당 상품이 없습니다. productId=" + productId));
        item.setQuantity(quantity);
        return cartItemRepository.save(item);
    }

    @Transactional(readOnly = true)
    public List<CartItem> getCart(Long userId) {
        return cartItemRepository.findByUserIdOrderByProductId(userId);
    }

    @Transactional
    public void removeItem(Long userId, Long productId) {
        cartItemRepository.deleteByUserIdAndProductId(userId, productId);
    }

    @Transactional
    public void clearCart(Long userId) {
        cartItemRepository.deleteByUserId(userId);
    }
}
