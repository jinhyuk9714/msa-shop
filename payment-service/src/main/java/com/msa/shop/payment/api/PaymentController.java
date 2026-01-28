package com.msa.shop.payment.api;

import com.msa.shop.payment.application.PaymentService;
import com.msa.shop.payment.domain.Payment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

record PaymentRequest(Long userId, int amount, String paymentMethod) {}

record PaymentResponse(boolean success, Long paymentId, String reason) {}

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

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
}

