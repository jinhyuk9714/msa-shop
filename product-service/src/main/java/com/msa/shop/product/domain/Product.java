package com.msa.shop.product.domain;

import jakarta.persistence.*;

/**
 * 상품 엔티티. product-service DB(products 테이블)와 1:1 매핑.
 * - 재고 차감은 decreaseStock()으로. 호출 전 수량 검증 필요.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** 카테고리(예: 전자, 생활, 식품). 검색·필터용. */
    @Column(length = 50)
    private String category;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stockQuantity;

    protected Product() {
    }

    public Product(String name, int price, int stockQuantity) {
        this(name, null, price, stockQuantity);
    }

    public Product(String name, String category, int price, int stockQuantity) {
        this.name = name;
        this.category = category;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public int getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    /**
     * 재고 차감. 내부에서 수량·재고 검증 후 예외 또는 차감.
     * - 0 이하, 재고 초과 시 IllegalArgumentException.
     */
    public void decreaseStock(int quantity) {
        if (quantity <= 0 || stockQuantity < quantity) {
            throw new IllegalArgumentException("유효하지 않은 재고 차감 요청");
        }
        this.stockQuantity -= quantity;
    }

    /**
     * 재고 복구(보상 트랜잭션용). quantity 만큼 재고를 다시 늘린다.
     * - 0 이하 수량은 허용하지 않음.
     */
    public void increaseStock(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("유효하지 않은 재고 복구 요청");
        }
        this.stockQuantity += quantity;
    }
}

