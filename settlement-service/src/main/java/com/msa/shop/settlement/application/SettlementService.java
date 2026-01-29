package com.msa.shop.settlement.application;

import com.msa.shop.settlement.domain.DailySettlement;
import com.msa.shop.settlement.domain.DailySettlementRepository;
import com.msa.shop.settlement.domain.MonthlySettlement;
import com.msa.shop.settlement.domain.MonthlySettlementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * 결제 완료 이벤트를 받아 일별 매출 집계를 갱신.
 * - recordPaymentCompleted: 해당 일자의 DailySettlement에 금액·건수 반영.
 * - runMonthlySettlement: 해당 월의 DailySettlement를 합산해 MonthlySettlement 생성/갱신.
 */
@Service
public class SettlementService {

    private final DailySettlementRepository dailySettlementRepository;
    private final MonthlySettlementRepository monthlySettlementRepository;

    public SettlementService(DailySettlementRepository dailySettlementRepository,
                             MonthlySettlementRepository monthlySettlementRepository) {
        this.dailySettlementRepository = dailySettlementRepository;
        this.monthlySettlementRepository = monthlySettlementRepository;
    }

    /**
     * 결제 완료 이벤트 처리. payment-service가 결제 승인 후 호출.
     * - paidAt 기준 일자로 집계 row를 찾거나 생성 후 totalAmount, paymentCount 갱신.
     */
    @Transactional
    public void recordPaymentCompleted(Long paymentId, Long userId, int amount, LocalDateTime paidAt) {
        LocalDate date = paidAt.toLocalDate();
        DailySettlement daily = dailySettlementRepository.findBySettlementDate(date)
                .orElseGet(() -> dailySettlementRepository.save(new DailySettlement(date)));
        daily.addPayment(amount);
        dailySettlementRepository.save(daily);
    }

    @Transactional(readOnly = true)
    public DailySettlement getDailySettlement(LocalDate date) {
        return dailySettlementRepository.findBySettlementDate(date)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<DailySettlement> getRecentDailySettlements(int limit) {
        return dailySettlementRepository.findTop30ByOrderBySettlementDateDesc().stream()
                .limit(limit)
                .toList();
    }

    /**
     * 해당 일자에 DailySettlement가 없으면 0원·0건으로 생성. 일별 배치에서 전일 마감 시 호출.
     */
    @Transactional
    public void ensureDailyRow(LocalDate date) {
        if (dailySettlementRepository.findBySettlementDate(date).isEmpty()) {
            dailySettlementRepository.save(new DailySettlement(date));
        }
    }

    /**
     * 해당 월의 일별 집계를 합산해 월별 집계 생성 또는 갱신. 배치 Job에서 호출.
     */
    @Transactional
    public void runMonthlySettlement(YearMonth yearMonth) {
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();
        List<DailySettlement> dailyList = dailySettlementRepository
                .findBySettlementDateBetweenOrderBySettlementDateAsc(start, end);
        long totalAmount = dailyList.stream().mapToLong(DailySettlement::getTotalAmount).sum();
        int paymentCount = dailyList.stream().mapToInt(DailySettlement::getPaymentCount).sum();
        upsertMonthly(yearMonth, totalAmount, paymentCount);
    }

    /** 기존 MonthlySettlement를 덮어쓰기 위해 엔티티에 setter가 없으므로, 있으면 삭제 후 새로 저장. */
    private void upsertMonthly(YearMonth yearMonth, long totalAmount, int paymentCount) {
        monthlySettlementRepository.findByYearMonth(yearMonth).ifPresent(monthlySettlementRepository::delete);
        monthlySettlementRepository.save(new MonthlySettlement(yearMonth, totalAmount, paymentCount));
    }

    @Transactional(readOnly = true)
    public MonthlySettlement getMonthlySettlement(YearMonth yearMonth) {
        return monthlySettlementRepository.findByYearMonth(yearMonth).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<MonthlySettlement> getRecentMonthlySettlements(int limit) {
        return monthlySettlementRepository.findTop12ByOrderByYearMonthDesc().stream()
                .limit(limit)
                .toList();
    }
}
