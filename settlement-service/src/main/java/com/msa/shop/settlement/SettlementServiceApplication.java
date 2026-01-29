package com.msa.shop.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** settlement-service 진입점. 포트 8085. 결제 완료 이벤트 소비·일별/월별 매출 집계·배치 Job. */
@SpringBootApplication
@EnableScheduling
public class SettlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
