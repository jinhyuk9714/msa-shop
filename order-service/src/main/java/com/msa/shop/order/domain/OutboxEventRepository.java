package com.msa.shop.order.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Outbox 이벤트 조회·갱신.
 * - 스케줄러가 PENDING 이벤트를 조회해 보상 실행 후 PROCESSED/FAILED로 갱신.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    List<OutboxEvent> findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus status);
}
