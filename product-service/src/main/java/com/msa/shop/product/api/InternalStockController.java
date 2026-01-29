package com.msa.shop.product.api;

import com.msa.shop.product.domain.Product;
import com.msa.shop.product.domain.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** order-service → product-service 내부 호출용. 재고 예약 요청 DTO. */
record ReserveStockRequest(Long userId, Long productId, int quantity) {}

/** 재고 예약 결과. success=false면 "재고 부족" 등. */
record ReserveStockResponse(boolean success, String reason, int remainingStock) {}

/**
 * 내부 전용 API: 재고 예약/차감 및 복구(보상).
 * - order-service가 주문 플로우 중 POST /internal/stocks/reserve 로 호출.
 * - 재고 부족 시 success=false 반환. 200 OK + body로 구분 (REST 스타일 유지).
 */
@RestController
public class InternalStockController {

    private final ProductRepository productRepository;

    public InternalStockController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @PostMapping("/internal/stocks/reserve")
    @Transactional
    public ResponseEntity<ReserveStockResponse> reserve(@RequestBody ReserveStockRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. id=" + request.productId()));

        if (product.getStockQuantity() < request.quantity()) {
            return ResponseEntity.ok(
                    new ReserveStockResponse(false, "재고 부족", product.getStockQuantity())
            );
        }
        product.decreaseStock(request.quantity());
        productRepository.save(product);

        return ResponseEntity.ok(
                new ReserveStockResponse(true, "성공", product.getStockQuantity())
        );
    }

    /**
     * 재고 복구(보상 트랜잭션용).
     * - 결제 실패 등으로 예약했던 재고를 다시 되돌릴 때 사용.
     */
    @PostMapping("/internal/stocks/release")
    @Transactional
    public ResponseEntity<ReserveStockResponse> release(@RequestBody ReserveStockRequest request) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. id=" + request.productId()));

        product.increaseStock(request.quantity());
        productRepository.save(product);

        return ResponseEntity.ok(
                new ReserveStockResponse(true, "해제", product.getStockQuantity())
        );
    }
}
