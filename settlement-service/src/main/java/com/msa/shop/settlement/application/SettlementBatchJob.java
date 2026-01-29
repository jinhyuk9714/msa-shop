package com.msa.shop.settlement.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 일별/월별 집계 배치 Job.
 * - 일별: 매일 00:10에 전일 DailySettlement가 없으면 0원·0건 row 생성(전일 마감).
 * - 월별: 매월 1일 01:00에 전월 DailySettlement를 합산해 MonthlySettlement 생성/갱신.
 */
@Component
public class SettlementBatchJob {

    private static final Logger log = LoggerFactory.getLogger(SettlementBatchJob.class);

    private final SettlementService settlementService;

    public SettlementBatchJob(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    /** 매일 00:10 KST. 전일 일별 row 없으면 생성. */
    @Scheduled(cron = "${app.batch.daily-cron:0 10 0 * * ?}")
    public void dailySettlementJob() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Daily settlement batch: ensuring row for {}", yesterday);
        settlementService.ensureDailyRow(yesterday);
    }

    /** 매월 1일 01:00. 전월 월별 집계 실행. */
    @Scheduled(cron = "${app.batch.monthly-cron:0 0 1 1 * ?}")
    public void monthlySettlementJob() {
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        log.info("Monthly settlement batch: aggregating {}", lastMonth);
        settlementService.runMonthlySettlement(lastMonth);
    }
}
