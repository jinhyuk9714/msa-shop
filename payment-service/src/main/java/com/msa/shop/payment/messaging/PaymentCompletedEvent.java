package com.msa.shop.payment.messaging;

import java.time.LocalDateTime;

/**
 * 결제 완료 이벤트. RabbitMQ로 발행 후 settlement-service가 구독해 매출 집계.
 */
public record PaymentCompletedEvent(
        Long paymentId,
        Long userId,
        int amount,
        LocalDateTime paidAt
) {}
