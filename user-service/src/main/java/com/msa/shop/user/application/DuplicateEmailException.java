package com.msa.shop.user.application;

/** 회원가입 시 이메일 중복. ControllerAdvice에서 409 CONFLICT로 변환. */
public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException(String message) {
        super(message);
    }
}
