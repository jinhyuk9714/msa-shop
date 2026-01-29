package com.msa.shop.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Payment 엔티티용 DB 접근 계층.
 * - JpaRepository 기본 메서드만 사용. 커스텀 쿼리 없음.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {
}
