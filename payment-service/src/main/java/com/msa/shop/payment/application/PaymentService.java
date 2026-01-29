package com.msa.shop.payment.application;

import com.msa.shop.payment.domain.Payment;
import com.msa.shop.payment.domain.PaymentRepository;
import com.msa.shop.payment.domain.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 도메인 비즈니스 로직.
 * - "가짜 PG": amount > 0 이면 APPROVED 저장. 실패 규칙은 여기서 확장 가능.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * 결제 승인. 단순 룰: amount > 0 이면 성공, 그 외 IllegalArgumentException.
     * - 2단계에서 "결제 완료 이벤트" 발행 → settlement-service가 소비해 매출 집계.
     */
    @Transactional
    public Payment approve(Long userId, int amount, String paymentMethod) {
        if (amount <= 0) {
            throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
        }
        Payment payment = new Payment(userId, amount, paymentMethod, PaymentStatus.APPROVED);
        return paymentRepository.save(payment);
    }
}
