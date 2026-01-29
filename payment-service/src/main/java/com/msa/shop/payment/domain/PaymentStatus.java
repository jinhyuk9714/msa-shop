package com.msa.shop.payment.domain;

/**
 * 결제 상태.
 * - APPROVED: 승인 완료. 현재 플로우에서는 이 상태만 사용.
 * - CANCELED: (2단계) 결제 취소 API 등에서 사용 예정.
 */
public enum PaymentStatus {
    APPROVED,
    CANCELED
}
