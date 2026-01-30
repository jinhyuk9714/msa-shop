package com.msa.shop.product;

import com.msa.shop.product.domain.Product;
import com.msa.shop.product.domain.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 기동 시 테스트용 상품 3종 자동 등록.
 * - 이미 데이터 있으면 스킵 (재기동 시 중복 방지).
 * - E2E / 로컬 실행 시 상품 없이 주문 불가하므로 시딩 필요.
 */
@Component
public class ProductDataLoader implements CommandLineRunner {

    private final ProductRepository productRepository;

    public ProductDataLoader(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) {
            return;
        }
        productRepository.save(new Product("테스트 상품 A", "전자", 10_000, 100));
        productRepository.save(new Product("테스트 상품 B", "생활", 25_000, 50));
        productRepository.save(new Product("테스트 상품 C", "식품", 5_000, 5));
    }
}
