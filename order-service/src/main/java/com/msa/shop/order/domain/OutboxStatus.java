package com.msa.shop.order.domain;

/**
 * Outbox 이벤트 처리 상태.
 * - PENDING: 아직 보상 미실행.
 * - PROCESSED: 보상 완료(결제 취소·재고 복구 등).
 * - FAILED: 보상 시도 중 오류(재시도 가능하도록 남겨둘 수 있음).
 */
public enum OutboxStatus {
    PENDING,
    PROCESSED,
    FAILED
}
