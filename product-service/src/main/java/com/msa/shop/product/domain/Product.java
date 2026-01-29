package com.msa.shop.product.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stockQuantity;

    protected Product() {
    }

    public Product(String name, int price, int stockQuantity) {
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    /** 재고 차감. 호출 전 quantity 검증 필요. */
    public void decreaseStock(int quantity) {
        if (quantity <= 0 || stockQuantity < quantity) {
            throw new IllegalArgumentException("유효하지 않은 재고 차감 요청");
        }
        this.stockQuantity -= quantity;
    }
}

