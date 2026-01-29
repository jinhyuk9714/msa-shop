package com.msa.shop.user.domain;

import jakarta.persistence.*;

/**
 * 회원 엔티티. user-service DB(users 테이블)와 1:1 매핑.
 * - JPA: Java 객체 ↔ DB 테이블 매핑. 저장/조회 시 자동 변환.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    /** JPA용. 실제로 new User(...) 시 사용하지 않음. */
    protected User() {
    }

    public User(String email, String password, String name) {
        this.email = email;
        this.password = password;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getName() {
        return name;
    }
}
