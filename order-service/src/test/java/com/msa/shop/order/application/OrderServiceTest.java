package com.msa.shop.order.application;

import com.msa.shop.order.domain.Order;
import com.msa.shop.order.domain.OrderRepository;
import com.msa.shop.order.domain.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    static final String PRODUCT_BASE = "http://localhost:8082";
    static final String PAYMENT_BASE = "http://localhost:8084";

    RestTemplate restTemplate = new RestTemplate();
    MockRestServiceServer server;

    @Mock
    OrderRepository orderRepository;

    OrderService orderService;

    @BeforeEach
    void setUp() {
        server = MockRestServiceServer.bindTo(restTemplate).build();
        orderService = new OrderService(orderRepository, restTemplate, PRODUCT_BASE, PAYMENT_BASE);
    }

    @Nested
    @DisplayName("createOrder")
    class CreateOrder {

        @Test
        @DisplayName("전체 성공 시 PAID 주문 저장")
        void success() {
            server.expect(requestTo(PRODUCT_BASE + "/products/1"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(
                            "{\"id\":1,\"name\":\"A\",\"price\":10000,\"stockQuantity\":10}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(PRODUCT_BASE + "/internal/stocks/reserve"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess(
                            "{\"success\":true,\"reason\":\"성공\",\"remainingStock\":8}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(PAYMENT_BASE + "/payments"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess(
                            "{\"success\":true,\"paymentId\":1,\"reason\":\"APPROVED\"}",
                            MediaType.APPLICATION_JSON));

            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.createOrder(1L, 1L, 2, "CARD");

            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(result.getProductId()).isEqualTo(1L);
            assertThat(result.getQuantity()).isEqualTo(2);
            assertThat(result.getTotalAmount()).isEqualTo(20_000);
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
            server.verify();
        }

        @Test
        @DisplayName("재고 예약 실패 시 IllegalStateException")
        void reserveFails() {
            server.expect(requestTo(PRODUCT_BASE + "/products/1"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(
                            "{\"id\":1,\"name\":\"A\",\"price\":10000,\"stockQuantity\":10}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(PRODUCT_BASE + "/internal/stocks/reserve"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess(
                            "{\"success\":false,\"reason\":\"재고 부족\",\"remainingStock\":0}",
                            MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> orderService.createOrder(1L, 1L, 100, "CARD"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("재고 부족");
            server.verify();
        }

        @Test
        @DisplayName("결제 실패 시 IllegalStateException")
        void paymentFails() {
            server.expect(requestTo(PRODUCT_BASE + "/products/1"))
                    .andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(
                            "{\"id\":1,\"name\":\"A\",\"price\":10000,\"stockQuantity\":10}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(PRODUCT_BASE + "/internal/stocks/reserve"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess(
                            "{\"success\":true,\"reason\":\"성공\",\"remainingStock\":8}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(PAYMENT_BASE + "/payments"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess(
                            "{\"success\":false,\"paymentId\":null,\"reason\":\"결제 실패\"}",
                            MediaType.APPLICATION_JSON));

            assertThatThrownBy(() -> orderService.createOrder(1L, 1L, 2, "CARD"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("결제 실패");
            server.verify();
        }
    }

    @Nested
    @DisplayName("getOrder")
    class GetOrder {

        @Test
        @DisplayName("존재하면 반환")
        void success() {
            Order order = new Order(1L, 1L, 2, 20_000, OrderStatus.PAID);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            Order result = orderService.getOrder(1L);

            assertThat(result.getUserId()).isEqualTo(1L);
            assertThat(result.getProductId()).isEqualTo(1L);
            assertThat(result.getTotalAmount()).isEqualTo(20_000);
        }

        @Test
        @DisplayName("없으면 IllegalArgumentException")
        void notFound() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrder(999L))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("주문을 찾을 수 없습니다");
        }
    }
}
