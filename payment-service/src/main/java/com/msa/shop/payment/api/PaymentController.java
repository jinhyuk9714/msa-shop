package com.msa.shop.payment.api;

import com.msa.shop.payment.application.PaymentService;
import com.msa.shop.payment.domain.Payment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** order-service → payment-service 결제 요청 DTO. */
record PaymentRequest(Long userId, int amount, String paymentMethod) {}

/** 결제 결과. success=false면 reason에 에러 메시지. */
record PaymentResponse(boolean success, Long paymentId, String reason) {}

/**
 * payment-service HTTP API 진입점.
 * - "가짜 PG" 역할. order-service가 POST /payments 로 결제 요청.
 * - 현재는 amount > 0 이면 승인, 0 이하면 400 + success=false 반환.
 */
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * 결제 시도. 성공 시 APPROVED 저장, 200 + success=true.
     * - 금액 오류 등 → 400 + success=false, reason에 메시지.
     */
    @PostMapping("/payments")
    public ResponseEntity<PaymentResponse> pay(@RequestBody PaymentRequest request) {
        try {
            Payment payment = paymentService.approve(
                    request.userId(),
                    request.amount(),
                    request.paymentMethod()
            );
            return ResponseEntity.ok(
                    new PaymentResponse(true, payment.getId(), "APPROVED")
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new PaymentResponse(false, null, e.getMessage()));
        }
    }

    /**
     * 결제 취소. order-service 보상 트랜잭션(주문 저장 실패 시)에서 호출.
     * - 200: 취소 완료. 404: 해당 결제 없음.
     */
    @PostMapping("/payments/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable("id") Long id) {
        try {
            paymentService.cancel(id);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
