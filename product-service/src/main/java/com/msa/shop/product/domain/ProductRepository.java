package com.msa.shop.product.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Product 엔티티용 DB 접근 계층.
 * - JpaRepository: CRUD + findAll, findById 등 제공. 커스텀 메서드 없이 기본만 사용.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {
}
