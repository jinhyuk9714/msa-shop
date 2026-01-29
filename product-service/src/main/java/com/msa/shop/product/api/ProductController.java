package com.msa.shop.product.api;

import com.msa.shop.product.application.ProductService;
import com.msa.shop.product.domain.Product;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 상품 API 응답 DTO. 재고 포함. */
record ProductResponse(Long id, String name, int price, int stockQuantity) {
    static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStockQuantity()
        );
    }
}

/**
 * product-service HTTP API 진입점 (공개용).
 * - 상품 목록/상세 조회. order-service는 가격·재고 확인을 위해 GET /products/{id} 호출.
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 상품 목록. E2E·테스트 시 ProductDataLoader로 시딩된 데이터 조회. */
    @GetMapping
    public List<ProductResponse> getProducts() {
        return productService.getProducts()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    /** 상품 상세. order-service가 주문 금액 계산 시 사용. */
    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        return ProductResponse.from(productService.getProduct(id));
    }
}
