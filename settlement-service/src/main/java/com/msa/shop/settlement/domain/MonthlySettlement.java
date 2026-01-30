package com.msa.shop.settlement.domain;

import jakarta.persistence.*;

import java.time.YearMonth;

/**
 * 월별 매출 집계. 배치 Job이 DailySettlement를 합산해 생성.
 * yearMonth는 YearMonthAttributeConverter로 DB에 "yyyy-MM" 문자열 저장.
 */
@Entity
@Table(name = "monthly_settlements", uniqueConstraints = @UniqueConstraint(columnNames = "settlement_year_month"))
public class MonthlySettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_year_month", nullable = false, unique = true)
    private YearMonth yearMonth;

    @Column(nullable = false)
    private long totalAmount = 0;

    @Column(nullable = false)
    private int paymentCount = 0;

    protected MonthlySettlement() {
    }

    public MonthlySettlement(YearMonth yearMonth, long totalAmount, int paymentCount) {
        this.yearMonth = yearMonth;
        this.totalAmount = totalAmount;
        this.paymentCount = paymentCount;
    }

    public Long getId() {
        return id;
    }

    public YearMonth getYearMonth() {
        return yearMonth;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public int getPaymentCount() {
        return paymentCount;
    }
}
