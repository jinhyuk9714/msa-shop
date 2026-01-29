package com.msa.shop.order.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Outbox 테이블: "결제 성공 후 주문 저장 실패" 등 보상 이벤트를 DB에 기록.
 * - 같은 DB 트랜잭션으로 비즈니스 데이터와 함께 쓸 수 없을 때(저장이 이미 실패),
 *   별도 TX로 이 테이블에만 기록 → 스케줄러가 읽어 결제 취소·재고 복구 등 보상 실행.
 * - eventType: ORDER_SAVE_FAILED 등. payload: JSON { paymentId, userId, productId, quantity }.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime processedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String eventType, String payload) {
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayload() {
        return payload;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public void markProcessed() {
        this.status = OutboxStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = OutboxStatus.FAILED;
        this.processedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
