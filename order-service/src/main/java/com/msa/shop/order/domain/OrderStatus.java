package com.msa.shop.order.domain;

/**
 * 주문 상태.
 * - PAID: 결제 완료, 주문 저장됨.
 * - FAILED: 결제 실패·보상 등으로 주문 실패.
 * - CANCELLED: 사용자 취소 또는 환불 완료.
 */
public enum OrderStatus {
    PAID,
    FAILED,
    CANCELLED
}
