package com.msa.shop.order.application;

/** 결제 요청 실패. ControllerAdvice에서 402 PAYMENT_REQUIRED로 변환. */
public class PaymentFailedException extends RuntimeException {

    public PaymentFailedException(String message) {
        super(message);
    }
}
