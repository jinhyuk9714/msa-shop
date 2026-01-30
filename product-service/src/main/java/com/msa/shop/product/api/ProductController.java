package com.msa.shop.product.api;

import com.msa.shop.product.application.ProductService;
import com.msa.shop.product.domain.Product;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 상품 API 응답 DTO. 재고·카테고리 포함. */
record ProductResponse(Long id, String name, String category, int price, int stockQuantity) {
    static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getPrice(),
                product.getStockQuantity()
        );
    }
}

/**
 * product-service HTTP API 진입점 (공개용).
 * - 상품 목록/상세 조회. order-service는 가격·재고 확인을 위해 GET /products/{id} 호출.
 * - GET /products?name=...&minPrice=...&maxPrice=... 로 검색 가능.
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 상품 목록 또는 검색. 쿼리 파라미터가 없으면 전체 목록, 있으면 조건 검색.
     * - name: 상품명 부분 일치 (대소문자 무시)
     * - category: 카테고리 일치 (예: 전자, 생활, 식품)
     * - minPrice, maxPrice: 가격 범위 (포함)
     */
    @GetMapping
    public List<ProductResponse> getProducts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer minPrice,
            @RequestParam(required = false) Integer maxPrice
    ) {
        boolean hasSearch = (name != null && !name.isBlank()) || (category != null && !category.isBlank()) || minPrice != null || maxPrice != null;
        List<Product> products = hasSearch
                ? productService.searchProducts(
                        name != null ? name.strip() : null,
                        category != null ? category.strip() : null,
                        minPrice, maxPrice)
                : productService.getProducts();
        return products.stream().map(ProductResponse::from).toList();
    }

    /** 상품 상세. order-service가 주문 금액 계산 시 사용. */
    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        return ProductResponse.from(productService.getProduct(id));
    }
}
