package com.msa.shop.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정 (user-service).
 * - permitAll: 인증 없이 접근 허용 (회원가입, 로그인, /users/me, actuator, H2 콘솔).
 * - anyRequest().authenticated(): 그 외 API는 인증 필요 (현재 더미 토큰이라 별도 필터 없음).
 * - CSRF disable: REST API에서는 보통 비활성화. 쿠키 기반 폼 로그인 시에는 활성화.
 * - frameOptions disable: H2 콘솔이 iframe 사용하기 때문.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/users", "/users/me", "/auth/login", "/actuator/**", "/h2-console/**").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(Customizer.withDefaults());

        http.headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}
