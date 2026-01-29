package com.msa.shop.user.application;

/** 조회한 사용자 없음. ControllerAdvice에서 404 NOT_FOUND로 변환. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
