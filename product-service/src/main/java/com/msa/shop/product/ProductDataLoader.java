package com.msa.shop.product;

import com.msa.shop.product.domain.Product;
import com.msa.shop.product.domain.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

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
        productRepository.save(new Product("테스트 상품 A", 10_000, 100));
        productRepository.save(new Product("테스트 상품 B", 25_000, 50));
        productRepository.save(new Product("테스트 상품 C", 5_000, 5));
    }
}
