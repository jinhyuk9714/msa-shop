package com.msa.shop.product.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    @Nested
    @DisplayName("decreaseStock")
    class DecreaseStock {

        @Test
        @DisplayName("유효한 수량이면 재고 차감")
        void success() {
            Product product = new Product("상품", 10_000, 10);

            product.decreaseStock(3);

            assertThat(product.getStockQuantity()).isEqualTo(7);
        }

        @Test
        @DisplayName("수량 0 이하면 IllegalArgumentException")
        void quantityZeroOrNegative() {
            Product product = new Product("상품", 10_000, 10);

            assertThatThrownBy(() -> product.decreaseStock(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("유효하지 않은 재고 차감 요청");
            assertThatThrownBy(() -> product.decreaseStock(-1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("유효하지 않은 재고 차감 요청");
        }

        @Test
        @DisplayName("재고 초과 수량이면 IllegalArgumentException")
        void quantityExceedsStock() {
            Product product = new Product("상품", 10_000, 5);

            assertThatThrownBy(() -> product.decreaseStock(10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("유효하지 않은 재고 차감 요청");
        }
    }
}
