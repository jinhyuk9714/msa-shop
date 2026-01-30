package com.msa.shop.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/** user-service 진입점. 포트 8081, 회원/로그인/내정보 API. */
@SpringBootApplication(exclude = { UserDetailsServiceAutoConfiguration.class })
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}

