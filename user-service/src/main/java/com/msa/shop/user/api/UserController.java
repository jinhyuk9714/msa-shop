package com.msa.shop.user.api;

import com.msa.shop.user.application.UserService;
import com.msa.shop.user.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// ========== API 요청/응답 DTO (Java 16+ record) ==========
// record: 불변 데이터 클래스. getter, equals, hashCode 자동 생성.
record RegisterUserRequest(String email, String password, String name) {}

record RegisterUserResponse(Long id, String email, String name) {
    static RegisterUserResponse from(User user) {
        return new RegisterUserResponse(user.getId(), user.getEmail(), user.getName());
    }
}

record LoginRequest(String email, String password) {}

record LoginResponse(String accessToken) {}

/** GET /users/me 응답. 비밀번호는 절대 반환하지 않음. */
record MeResponse(Long id, String email, String name) {
    static MeResponse from(com.msa.shop.user.domain.User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getName());
    }
}

/**
 * user-service HTTP API 진입점.
 * - 회원가입, 로그인, 내 정보 조회 제공.
 * - 현재는 더미 토큰(dummy-token-for-user-{id}) 사용. 추후 JWT로 교체 예정.
 */
@RestController
@RequestMapping
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 내 정보 조회. Authorization 헤더에서 userId 추출 후 DB 조회.
     * - 토큰 없거나 형식 오류 → InvalidTokenException → 401
     * - 사용자 없음 → UserNotFoundException → 404
     */
    @GetMapping("/users/me")
    public ResponseEntity<MeResponse> getMe(@RequestHeader("Authorization") String authorization) {
        Long userId = extractUserIdFromToken(authorization);
        var user = userService.getUser(userId);
        return ResponseEntity.ok(MeResponse.from(user));
    }

    /** 회원가입. 이메일 중복 시 DuplicateEmailException → 409 (ControllerAdvice 처리). */
    @PostMapping("/users")
    public ResponseEntity<RegisterUserResponse> register(@RequestBody RegisterUserRequest request) {
        User user = userService.register(request.email(), request.password(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RegisterUserResponse.from(user));
    }

    /**
     * 로그인. 성공 시 더미 액세스 토큰 발급.
     * - order-service 등에서 "Bearer {token}"으로 userId 파싱할 때 이 규칙 사용.
     */
    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        User user = userService.login(request.email(), request.password());
        String dummyToken = "dummy-token-for-user-" + user.getId();
        return ResponseEntity.ok(new LoginResponse(dummyToken));
    }

    /**
     * Authorization: Bearer dummy-token-for-user-{id} 에서 userId(Long) 추출.
     * 형식 오류 시 InvalidTokenException → 401.
     */
    private Long extractUserIdFromToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new com.msa.shop.user.application.InvalidTokenException("Authorization 헤더가 올바르지 않습니다.");
        }
        String token = authorizationHeader.substring("Bearer ".length());
        String prefix = "dummy-token-for-user-";
        if (!token.startsWith(prefix)) {
            throw new com.msa.shop.user.application.InvalidTokenException("토큰 형식이 올바르지 않습니다.");
        }
        try {
            return Long.parseLong(token.substring(prefix.length()));
        } catch (NumberFormatException e) {
            throw new com.msa.shop.user.application.InvalidTokenException("토큰 형식이 올바르지 않습니다.");
        }
    }
}
