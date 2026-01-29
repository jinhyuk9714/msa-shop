package com.msa.shop.product.api;

import com.msa.shop.product.domain.Product;
import com.msa.shop.product.domain.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

record ReserveStockRequest(Long userId, Long productId, int quantity) {}

record ReserveStockResponse(boolean success, String reason, int remainingStock) {}

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
}

