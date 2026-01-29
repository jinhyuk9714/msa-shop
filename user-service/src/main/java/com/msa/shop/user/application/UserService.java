package com.msa.shop.user.application;

import com.msa.shop.user.domain.User;
import com.msa.shop.user.domain.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원(User) 도메인 비즈니스 로직.
 * - Controller는 HTTP만, Service는 도메인 규칙만 담당 (관심사 분리).
 * - 비밀번호는 BCrypt로 해싱 저장·검증.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 회원가입. 이메일 유일 제약 검사. 비밀번호는 BCrypt 해싱 후 저장.
     * - 중복 시 DuplicateEmailException → ControllerAdvice에서 409 반환.
     */
    @Transactional
    public User register(String email, String password, String name) {
        userRepository.findByEmail(email).ifPresent(u -> {
            throw new DuplicateEmailException("이미 존재하는 이메일입니다.");
        });
        String encodedPassword = passwordEncoder.encode(password);
        User user = new User(email, encodedPassword, name);
        return userRepository.save(user);
    }

    /** id로 사용자 조회. 없으면 UserNotFoundException → 404. */
    @Transactional(readOnly = true)
    public User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다. id=" + id));
    }

    /**
     * 로그인: 이메일/비밀번호 검증. DB에는 해시 저장되어 있으므로 matches로 비교.
     * - 보안상 "이메일 없음"과 "비밀번호 틀림"을 구분하지 않고 동일 메시지 반환.
     */
    @Transactional(readOnly = true)
    public User login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
        return user;
    }
}
