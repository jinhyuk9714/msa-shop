package com.msa.shop.settlement.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * 일별 매출 집계. settlement-service DB에만 존재.
 * - payment-service에서 "결제 완료" 이벤트를 받을 때마다 해당 일자의 집계를 갱신.
 */
@Entity
@Table(name = "daily_settlements", uniqueConstraints = @UniqueConstraint(columnNames = "report_date"))
public class DailySettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** MySQL 예약어(date) 충돌 회피를 위해 report_date 사용 */
    @Column(name = "report_date", nullable = false, unique = true)
    private LocalDate settlementDate;

    @Column(nullable = false)
    private long totalAmount = 0;

    @Column(nullable = false)
    private int paymentCount = 0;

    protected DailySettlement() {
    }

    public DailySettlement(LocalDate settlementDate) {
        this.settlementDate = settlementDate;
    }

    public void addPayment(int amount) {
        this.totalAmount += amount;
        this.paymentCount += 1;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
    }

    public long getTotalAmount() {
        return totalAmount;
    }

    public int getPaymentCount() {
        return paymentCount;
    }
}
