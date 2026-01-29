package com.msa.shop.order.application;

/** Authorization 토큰 없음/형식 오류. ControllerAdvice에서 401 UNAUTHORIZED로 변환. */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }
}
