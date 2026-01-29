package com.msa.shop.settlement.api;

import com.msa.shop.settlement.application.SettlementService;
import com.msa.shop.settlement.domain.DailySettlement;
import com.msa.shop.settlement.domain.MonthlySettlement;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** 일별 집계 응답 DTO. */
record DailySettlementResponse(LocalDate settlementDate, long totalAmount, int paymentCount) {
    static DailySettlementResponse from(DailySettlement d) {
        return new DailySettlementResponse(d.getSettlementDate(), d.getTotalAmount(), d.getPaymentCount());
    }
}

/** 월별 집계 응답 DTO. */
record MonthlySettlementResponse(YearMonth yearMonth, long totalAmount, int paymentCount) {
    static MonthlySettlementResponse from(MonthlySettlement m) {
        return new MonthlySettlementResponse(m.getYearMonth(), m.getTotalAmount(), m.getPaymentCount());
    }
}

/**
 * settlement-service HTTP API.
 * - 결제 완료 이벤트는 RabbitMQ로 수신( PaymentCompletedListener ).
 * - GET /settlements/daily?date=yyyy-MM-dd: 해당 일자 집계.
 * - GET /settlements/daily: 최근 일별 집계 목록(최대 30일).
 * - GET /settlements/monthly?yearMonth=yyyy-MM: 해당 월 집계.
 * - GET /settlements/monthly: 최근 월별 집계 목록(최대 12개월).
 */
@RestController
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    /**
     * 특정 일자 매출 집계 조회. ?date=yyyy-MM-dd 없으면 최근 일별 목록(최대 30).
     */
    @GetMapping("/settlements/daily")
    public ResponseEntity<?> getDaily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        if (date == null) {
            List<DailySettlement> list = settlementService.getRecentDailySettlements(30);
            return ResponseEntity.ok(list.stream().map(DailySettlementResponse::from).toList());
        }
        DailySettlement daily = settlementService.getDailySettlement(date);
        if (daily == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(DailySettlementResponse.from(daily));
    }

    /**
     * 월별 매출 집계 조회. ?yearMonth=yyyy-MM 없으면 최근 월별 목록(최대 12개월).
     */
    @GetMapping("/settlements/monthly")
    public ResponseEntity<?> getMonthly(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonth
    ) {
        if (yearMonth == null) {
            return ResponseEntity.ok(
                    settlementService.getRecentMonthlySettlements(12).stream()
                            .map(MonthlySettlementResponse::from)
                            .toList()
            );
        }
        MonthlySettlement monthly = settlementService.getMonthlySettlement(yearMonth);
        if (monthly == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(MonthlySettlementResponse.from(monthly));
    }
}
