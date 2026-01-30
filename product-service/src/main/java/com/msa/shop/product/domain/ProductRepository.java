package com.msa.shop.product.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Product 엔티티용 DB 접근 계층.
 * - JpaRepository: CRUD + findAll, findById 등 제공.
 * - search: 이름(부분 일치), 카테고리(일치), 최소/최대 가격 조건 검색 (null 파라미터는 조건에서 제외).
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE (:name IS NULL OR :name = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%'))) " +
           "AND (:category IS NULL OR :category = '' OR p.category = :category) " +
           "AND (:minPrice IS NULL OR p.price >= :minPrice) AND (:maxPrice IS NULL OR p.price <= :maxPrice)")
    List<Product> search(@Param("name") String name, @Param("category") String category,
                         @Param("minPrice") Integer minPrice, @Param("maxPrice") Integer maxPrice);
}
