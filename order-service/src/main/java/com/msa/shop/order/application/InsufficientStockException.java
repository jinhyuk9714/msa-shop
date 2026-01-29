package com.msa.shop.order.application;

/** 재고 예약 실패(재고 부족). ControllerAdvice에서 409 CONFLICT로 변환. */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
