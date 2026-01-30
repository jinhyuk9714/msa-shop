package com.msa.shop.product.application;

import com.msa.shop.product.domain.Product;
import com.msa.shop.product.domain.ProductRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 상품 도메인 비즈니스 로직.
 * - 목록/상세 조회. 재고 예약은 InternalStockController에서 직접 Repository 사용.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "products", unless = "#result.isEmpty()")
    public List<Product> getProducts() {
        return productRepository.findAll();
    }

    /**
     * 상품 검색. name(부분 일치, 대소문자 무시), minPrice, maxPrice 중 null이 아닌 값만 조건 적용.
     */
    @Transactional(readOnly = true)
    public List<Product> searchProducts(String name, Integer minPrice, Integer maxPrice) {
        return productRepository.search(name, minPrice, maxPrice);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "product", key = "#id")
    public Product getProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다. id=" + id));
    }
}
