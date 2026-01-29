package com.msa.shop.user.api;

import com.msa.shop.user.application.InvalidTokenException;
import com.msa.shop.user.application.UserService;
import com.msa.shop.user.config.JwtService;
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
 * - 회원가입, 로그인(JWT 발급), 내 정보 조회(JWT 검증).
 */
@RestController
@RequestMapping
public class UserController {

    private final UserService userService;
    private final JwtService jwtService;

    public UserController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    /**
     * 내 정보 조회. API Gateway 경유 시 X-User-Id, 직접 호출 시 Authorization Bearer JWT.
     * - 토큰/헤더 없거나 형식 오류 → InvalidTokenException → 401
     * - 사용자 없음 → UserNotFoundException → 404
     */
    @GetMapping("/users/me")
    public ResponseEntity<MeResponse> getMe(
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        Long userId = resolveUserId(xUserId, authorization);
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

    /** 로그인. 성공 시 JWT 액세스 토큰 발급. order-service 등에서 Bearer JWT로 검증·userId 추출. */
    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        User user = userService.login(request.email(), request.password());
        String accessToken = jwtService.generateToken(user.getId(), user.getEmail());
        return ResponseEntity.ok(new LoginResponse(accessToken));
    }

    /** Gateway 경유 시 X-User-Id, 직접 호출 시 Authorization Bearer JWT. */
    private Long resolveUserId(String xUserId, String authorization) {
        if (xUserId != null && !xUserId.isBlank()) {
            try {
                return Long.parseLong(xUserId.trim());
            } catch (NumberFormatException e) {
                throw new InvalidTokenException("X-User-Id가 올바르지 않습니다.");
            }
        }
        return extractUserIdFromToken(authorization);
    }

    private Long extractUserIdFromToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Authorization 헤더가 올바르지 않습니다.");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        return jwtService.parseUserIdFromToken(token);
    }
}
