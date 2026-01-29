package com.msa.shop.settlement.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface MonthlySettlementRepository extends JpaRepository<MonthlySettlement, Long> {

    Optional<MonthlySettlement> findByYearMonth(YearMonth yearMonth);

    List<MonthlySettlement> findTop12ByOrderByYearMonthDesc();
}
