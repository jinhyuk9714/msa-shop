package com.msa.shop.payment.application;

import com.msa.shop.payment.domain.Payment;
import com.msa.shop.payment.domain.PaymentRepository;
import com.msa.shop.payment.domain.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    PaymentRepository paymentRepository;

    @InjectMocks
    PaymentService paymentService;

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        @DisplayName("금액 > 0 이면 저장 후 반환")
        void success() {
            Payment saved = new Payment(1L, 10_000, "CARD", PaymentStatus.APPROVED);
            when(paymentRepository.save(any(Payment.class))).thenReturn(saved);

            Payment result = paymentService.approve(1L, 10_000, "CARD");

            assertThat(result.getAmount()).isEqualTo(10_000);
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.APPROVED);
            verify(paymentRepository).save(any(Payment.class));
        }

        @Test
        @DisplayName("금액 <= 0 이면 IllegalArgumentException")
        void invalidAmount() {
            assertThatThrownBy(() -> paymentService.approve(1L, 0, "CARD"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("결제 금액이 올바르지 않습니다.");
            assertThatThrownBy(() -> paymentService.approve(1L, -100, "CARD"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("결제 금액이 올바르지 않습니다.");
        }
    }
}
