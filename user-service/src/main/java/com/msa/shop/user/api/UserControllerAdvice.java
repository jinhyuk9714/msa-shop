package com.msa.shop.user.api;

import com.msa.shop.user.application.DuplicateEmailException;
import com.msa.shop.user.application.InvalidTokenException;
import com.msa.shop.user.application.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * user-service 전역 예외 처리.
 * - Controller에서 throw된 예외를 잡아 HTTP 상태코드 + JSON body로 변환.
 * - 클라이언트는 항상 { "error", "message" } 형태로 에러 응답을 받음.
 */
@RestControllerAdvice
public class UserControllerAdvice {

    /** 이메일 중복 → 409 CONFLICT */
    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateEmail(DuplicateEmailException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("error", "CONFLICT", "message", ex.getMessage()));
    }

    /** 토큰 없음/형식 오류 → 401 UNAUTHORIZED */
    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<Map<String, String>> handleInvalidToken(InvalidTokenException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "UNAUTHORIZED", "message", ex.getMessage()));
    }

    /** 사용자 없음 → 404 NOT_FOUND */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound(UserNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "NOT_FOUND", "message", ex.getMessage()));
    }
}
