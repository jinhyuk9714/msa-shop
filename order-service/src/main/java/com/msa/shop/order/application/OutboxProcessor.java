package com.msa.shop.order.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msa.shop.order.domain.OutboxEvent;
import com.msa.shop.order.domain.OutboxEventRepository;
import com.msa.shop.order.domain.OutboxStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Outbox 이벤트 소비: PENDING 보상 이벤트를 주기적으로 읽어 결제 취소·재고 복구 실행.
 * - ORDER_SAVE_FAILED: payload의 paymentId로 결제 취소, userId/productId/quantity로 재고 복구.
 */
@Component
public class OutboxProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxProcessor.class);

    private final OutboxEventRepository outboxEventRepository;
    private final RestTemplate restTemplate;
    private final String productServiceBaseUrl;
    private final String paymentServiceBaseUrl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OutboxProcessor(
            OutboxEventRepository outboxEventRepository,
            RestTemplate restTemplate,
            @Value("${product-service.base-url}") String productServiceBaseUrl,
            @Value("${payment-service.base-url}") String paymentServiceBaseUrl
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.restTemplate = restTemplate;
        this.productServiceBaseUrl = productServiceBaseUrl;
        this.paymentServiceBaseUrl = paymentServiceBaseUrl;
    }

    @Scheduled(fixedDelayString = "${app.outbox.process-interval:5000}")
    @Transactional
    public void processPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop20ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        for (OutboxEvent event : pending) {
            if (!OutboxService.EVENT_ORDER_SAVE_FAILED.equals(event.getEventType())) {
                continue;
            }
            try {
                compensateOrderSaveFailed(event.getPayload());
                event.markProcessed();
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.warn("Outbox 보상 실패 eventId={} payload={}", event.getId(), event.getPayload(), e);
                event.markFailed();
                outboxEventRepository.save(event);
            }
        }
    }

    private void compensateOrderSaveFailed(String payload) throws Exception {
        JsonNode node = objectMapper.readTree(payload);
        long paymentId = node.get("paymentId").asLong();
        long userId = node.get("userId").asLong();
        long productId = node.get("productId").asLong();
        int quantity = node.get("quantity").asInt();

        // 1) 결제 취소
        String cancelUrl = paymentServiceBaseUrl + "/payments/" + paymentId + "/cancel";
        restTemplate.exchange(cancelUrl, HttpMethod.POST, null, Void.class);

        // 2) 재고 복구
        String releaseUrl = productServiceBaseUrl + "/internal/stocks/release";
        Map<String, Object> body = Map.of("userId", userId, "productId", productId, "quantity", quantity);
        restTemplate.postForEntity(releaseUrl, body, Void.class);
    }
}
