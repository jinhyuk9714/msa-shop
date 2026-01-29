package com.msa.shop.payment.application;

import com.msa.shop.payment.config.RabbitMQConfig;
import com.msa.shop.payment.domain.Payment;
import com.msa.shop.payment.domain.PaymentRepository;
import com.msa.shop.payment.domain.PaymentStatus;
import com.msa.shop.payment.messaging.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 도메인 비즈니스 로직.
 * - "가짜 PG": amount > 0 이면 APPROVED 저장. 실패 규칙은 여기서 확장 가능.
 * - 결제 완료 시 RabbitMQ로 결제 완료 이벤트 발행(settlement-service가 구독해 매출 집계). 발행 실패해도 결제는 성공 유지.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final RabbitTemplate rabbitTemplate;

    public PaymentService(PaymentRepository paymentRepository, RabbitTemplate rabbitTemplate) {
        this.paymentRepository = paymentRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 결제 승인. 단순 룰: amount > 0 이면 성공, 그 외 IllegalArgumentException.
     * - 저장 후 RabbitMQ로 "결제 완료" 이벤트 발행. settlement-service가 구독해 일별 매출 집계.
     */
    @Transactional
    public Payment approve(Long userId, int amount, String paymentMethod) {
        if (amount <= 0) {
            throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
        }
        Payment payment = new Payment(userId, amount, paymentMethod, PaymentStatus.APPROVED);
        payment = paymentRepository.save(payment);

        publishPaymentCompleted(payment);
        return payment;
    }

    private void publishPaymentCompleted(Payment payment) {
        try {
            PaymentCompletedEvent event = new PaymentCompletedEvent(
                    payment.getId(),
                    payment.getUserId(),
                    payment.getAmount(),
                    payment.getCreatedAt()
            );
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_PAYMENT_EVENTS,
                    RabbitMQConfig.ROUTING_KEY_PAYMENT_COMPLETED,
                    event
            );
        } catch (Exception e) {
            log.warn("결제 완료 이벤트 발행 실패 paymentId={}", payment.getId(), e);
        }
    }

    /**
     * 결제 취소. order-service에서 "결제 성공 후 주문 저장 실패" 시 보상으로 호출.
     * - APPROVED인 결제만 CANCELED로 변경. 이미 취소된 경우 무시.
     */
    @Transactional
    public void cancel(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("결제를 찾을 수 없습니다. id=" + paymentId));
        if (payment.getStatus() == PaymentStatus.APPROVED) {
            payment.cancel();
            paymentRepository.save(payment);
        }
    }
}
