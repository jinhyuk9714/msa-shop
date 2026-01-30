package com.msa.shop.order.domain;

import jakarta.persistence.*;

/**
 * 장바구니 항목. 사용자별·상품별 수량.
 */
@Entity
@Table(name = "cart_items", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "product_id" })
})
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    protected CartItem() {
    }

    public CartItem(Long userId, Long productId, int quantity) {
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}
