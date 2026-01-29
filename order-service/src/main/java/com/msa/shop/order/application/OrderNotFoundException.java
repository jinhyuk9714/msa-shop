package com.msa.shop.order.application;

/** 조회한 주문 없음. ControllerAdvice에서 404 NOT_FOUND로 변환. */
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String message) {
        super(message);
    }
}
