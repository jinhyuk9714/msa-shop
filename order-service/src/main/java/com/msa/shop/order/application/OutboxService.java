package com.msa.shop.order.application;

import com.msa.shop.order.domain.OutboxEvent;
import com.msa.shop.order.domain.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 이벤트 발행.
 * - REQUIRES_NEW: 주문 저장 TX가 실패해도 보상 이벤트만 별도 TX로 커밋.
 * - "결제 성공 후 주문 저장 실패" 시 이벤트를 남기면 스케줄러가 결제 취소·재고 복구 수행.
 */
@Service
public class OutboxService {

    public static final String EVENT_ORDER_SAVE_FAILED = "ORDER_SAVE_FAILED";

    private final OutboxEventRepository outboxEventRepository;

    public OutboxService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * 주문 저장 실패 보상 이벤트 발행. 별도 TX로 커밋해 두고, 스케줄러가 나중에 처리.
     * payload: JSON {"paymentId":1,"userId":1,"productId":1,"quantity":2}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishOrderSaveFailed(Long paymentId, Long userId, Long productId, int quantity) {
        String payload = String.format(
                "{\"paymentId\":%d,\"userId\":%d,\"productId\":%d,\"quantity\":%d}",
                paymentId, userId, productId, quantity
        );
        OutboxEvent event = new OutboxEvent(EVENT_ORDER_SAVE_FAILED, payload);
        outboxEventRepository.save(event);
    }
}
