package com.msa.shop.order.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 주문 엔티티. order-service DB(orders 테이블)와 1:1 매핑.
 * - userId: user-service 사용자 ID (다른 서비스 참조는 ID만 보관, DB FK 없음).
 * - productId: product-service 상품 ID.
 * - totalAmount: 주문 금액 (가격 × 수량). 결제 요청 시 payment-service에 전달.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private int totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    /** 결제 ID. 취소 시 payment-service /payments/{id}/cancel 호출에 사용. */
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Order() {
    }

    public Order(Long userId, Long productId, int quantity, int totalAmount, OrderStatus status, Long paymentId) {
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.totalAmount = totalAmount;
        this.status = status;
        this.paymentId = paymentId;
        this.createdAt = LocalDateTime.now();
    }

    /** 테스트·기존 데이터용. paymentId 없으면 취소 불가. */
    public Order(Long userId, Long productId, int quantity, int totalAmount, OrderStatus status) {
        this(userId, productId, quantity, totalAmount, status, null);
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
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

    public int getTotalAmount() {
        return totalAmount;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getPaymentId() {
        return paymentId;
    }
}
