package com.msa.shop.order.application;

/**
 * 주문 취소 불가 시 던짐 (이미 취소됨, 결제 정보 없음 등).
 */
public class OrderCannotBeCancelledException extends RuntimeException {

    public OrderCannotBeCancelledException(String message) {
        super(message);
    }
}
