package com.msa.shop.payment.application;

import com.msa.shop.payment.domain.Payment;
import com.msa.shop.payment.domain.PaymentRepository;
import com.msa.shop.payment.domain.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public Payment approve(Long userId, int amount, String paymentMethod) {
        // 아주 단순한 룰: 금액이 0 이하이면 실패
        if (amount <= 0) {
            throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
        }

        Payment payment = new Payment(userId, amount, paymentMethod, PaymentStatus.APPROVED);
        return paymentRepository.save(payment);
    }
}

