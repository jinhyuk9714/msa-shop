package com.msa.shop.user.application;

import com.msa.shop.user.domain.User;
import com.msa.shop.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Nested
    @DisplayName("회원가입")
    class Register {

        @Test
        @DisplayName("성공 시 비밀번호 해싱 후 저장·반환")
        void success() {
            when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User result = userService.register("new@test.com", "pw123", "신규");

            assertThat(result.getEmail()).isEqualTo("new@test.com");
            assertThat(result.getName()).isEqualTo("신규");
            assertThat(result.getPassword()).startsWith("$2a$");
            assertThat(result.getPassword()).isNotEqualTo("pw123");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("이미 존재하는 이메일이면 DuplicateEmailException")
        void duplicateEmail() {
            User existing = new User("dup@test.com", "pw", "기존");
            when(userRepository.findByEmail("dup@test.com")).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> userService.register("dup@test.com", "pw", "이름"))
                    .isInstanceOf(DuplicateEmailException.class)
                    .hasMessage("이미 존재하는 이메일입니다.");
        }
    }

    @Nested
    @DisplayName("로그인")
    class Login {

        @Test
        @DisplayName("성공 시 사용자 반환")
        void success() {
            String encoded = passwordEncoder.encode("pw123");
            User user = new User("a@test.com", encoded, "이름");
            when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(user));

            User result = userService.login("a@test.com", "pw123");

            assertThat(result.getEmail()).isEqualTo("a@test.com");
        }

        @Test
        @DisplayName("없는 이메일이면 IllegalArgumentException")
        void wrongEmail() {
            when(userRepository.findByEmail("none@test.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.login("none@test.com", "any"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        @Test
        @DisplayName("비밀번호 틀리면 IllegalArgumentException")
        void wrongPassword() {
            String encodedCorrect = passwordEncoder.encode("correct");
            User user = new User("a@test.com", encodedCorrect, "이름");
            when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.login("a@test.com", "wrong"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
    }

    @Nested
    @DisplayName("getUser")
    class GetUser {

        @Test
        @DisplayName("존재하면 반환")
        void success() {
            User user = new User("a@test.com", "pw", "이름");
            when(userRepository.findById(1L)).thenReturn(Optional.of(user));

            User result = userService.getUser(1L);

            assertThat(result.getEmail()).isEqualTo("a@test.com");
            assertThat(result.getName()).isEqualTo("이름");
        }

        @Test
        @DisplayName("없으면 UserNotFoundException")
        void notFound() {
            when(userRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUser(999L))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
        }
    }
}
