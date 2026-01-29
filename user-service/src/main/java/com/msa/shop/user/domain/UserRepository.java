package com.msa.shop.user.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * User 엔티티용 DB 접근 계층.
 * - JpaRepository: CRUD + findAll, findById 등 기본 메서드 제공.
 * - findByEmail: 메서드 이름만으로 JPQL 생성. "Email" → 컬럼 email 매핑.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
