package com.msa.shop.user.application;

import com.msa.shop.user.domain.User;
import com.msa.shop.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @InjectMocks
    UserService userService;

    @Nested
    @DisplayName("회원가입")
    class Register {

        @Test
        @DisplayName("성공 시 사용자 저장 후 반환")
        void success() {
            when(userRepository.findByEmail("new@test.com")).thenReturn(Optional.empty());
            User saved = new User("new@test.com", "pw123", "신규");
            when(userRepository.save(any(User.class))).thenReturn(saved);

            User result = userService.register("new@test.com", "pw123", "신규");

            assertThat(result.getEmail()).isEqualTo("new@test.com");
            assertThat(result.getName()).isEqualTo("신규");
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
            User user = new User("a@test.com", "pw123", "이름");
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
            User user = new User("a@test.com", "correct", "이름");
            when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.login("a@test.com", "wrong"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("이메일 또는 비밀번호가 올바르지 않습니다.");
        }
    }
}
