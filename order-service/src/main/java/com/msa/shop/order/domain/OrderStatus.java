package com.msa.shop.order.domain;

/**
 * 주문 상태.
 * - PAID: 결제 완료, 주문 저장됨.
 * - FAILED: (2단계) 결제 실패·보상 등으로 주문 실패 처리 시 사용 예정.
 */
public enum OrderStatus {
    PAID,
    FAILED
}
