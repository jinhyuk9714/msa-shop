package com.msa.shop.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Order 엔티티용 DB 접근 계층.
 * - findByUserIdOrderByCreatedAtDesc: "UserId" + "OrderBy" + "CreatedAt" + "Desc" → JPQL 자동 생성.
 *   사용자별 주문 목록, 최신순 정렬.
 */
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);
}
