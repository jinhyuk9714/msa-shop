package com.msa.shop.settlement.messaging;

import com.msa.shop.settlement.application.SettlementService;
import com.msa.shop.settlement.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ에서 결제 완료 이벤트를 구독해 settlement-service에 매출 집계 반영.
 */
@Component
public class PaymentCompletedListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentCompletedListener.class);

    private final SettlementService settlementService;

    public PaymentCompletedListener(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PAYMENT_COMPLETED)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.debug("결제 완료 이벤트 수신 paymentId={} amount={}", event.paymentId(), event.amount());
        settlementService.recordPaymentCompleted(
                event.paymentId(),
                event.userId(),
                event.amount(),
                event.paidAt()
        );
    }
}
