package com.msa.shop.settlement.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailySettlementRepository extends JpaRepository<DailySettlement, Long> {

    Optional<DailySettlement> findBySettlementDate(LocalDate date);

    List<DailySettlement> findTop30ByOrderBySettlementDateDesc();

    /** 해당 기간(이 inclusive) 내 일별 집계 조회. 월별 배치에서 사용. */
    List<DailySettlement> findBySettlementDateBetweenOrderBySettlementDateAsc(LocalDate start, LocalDate end);
}
