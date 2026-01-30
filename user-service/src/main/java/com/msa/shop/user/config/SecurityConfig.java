package com.msa.shop.user.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정 (user-service).
 * - Order(0): Swagger·OpenAPI(/v3/**, /swagger-ui/**, /webjars/**) 전부 permitAll.
 * - Order(1): 회원가입, 로그인, /users/me, actuator, H2 permitAll / 그 외 authenticated.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Swagger UI + OpenAPI 스펙: 이 경로들은 인증 없이 허용 (403 방지). */
    @Bean
    @Order(0)
    public SecurityFilterChain swaggerAndOpenApiFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/v3/**", "/v3/api-docs", "/api-docs.html", "/swagger-ui/**", "/swagger-ui.html", "/webjars/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.toAnyEndpoint()).permitAll()
                        .requestMatchers("/users", "/users/me", "/auth/login", "/h2-console/**", "/api-docs.html").permitAll()
                        .anyRequest().authenticated()
                )
                .httpBasic(basic -> basic.disable());

        http.headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}
