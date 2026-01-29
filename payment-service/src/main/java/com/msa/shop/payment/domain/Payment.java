package com.msa.shop.payment.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 결제 엔티티. payment-service DB(payments 테이블)와 1:1 매핑.
 * - userId: 주문자(user-service ID). order-service에서 전달.
 * - amount: 결제 금액. order-service가 가격×수량으로 계산 후 전달.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Payment() {
    }

    public Payment(Long userId, int amount, String paymentMethod, PaymentStatus status) {
        this.userId = userId;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public int getAmount() {
        return amount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
