package com.msa.shop.user.api;

import com.msa.shop.user.application.UserService;
import com.msa.shop.user.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

record RegisterUserRequest(String email, String password, String name) {}

record RegisterUserResponse(Long id, String email, String name) {
    static RegisterUserResponse from(User user) {
        return new RegisterUserResponse(user.getId(), user.getEmail(), user.getName());
    }
}

record LoginRequest(String email, String password) {}

record LoginResponse(String accessToken) {}

@RestController
@RequestMapping
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/users")
    public ResponseEntity<RegisterUserResponse> register(@RequestBody RegisterUserRequest request) {
        User user = userService.register(request.email(), request.password(), request.name());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RegisterUserResponse.from(user));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        User user = userService.login(request.email(), request.password());

        // TODO: 이후 JWT 적용. 현재는 더미 토큰 반환
        String dummyToken = "dummy-token-for-user-" + user.getId();

        return ResponseEntity.ok(new LoginResponse(dummyToken));
    }
}

