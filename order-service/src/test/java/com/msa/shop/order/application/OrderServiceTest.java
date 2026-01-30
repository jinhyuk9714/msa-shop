package com.msa.shop.order.application;

import com.msa.shop.order.domain.Order;
import com.msa.shop.order.domain.OrderRepository;
import com.msa.shop.order.domain.OrderStatus;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
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

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
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

    @Mock
    OutboxService outboxService;

    OrderService orderService;

    @BeforeEach
    void setUp() {
        server = MockRestServiceServer.bindTo(restTemplate).build();
        orderService = new OrderService(orderRepository, restTemplate, outboxService, PRODUCT_BASE, PAYMENT_BASE);
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
                    .isInstanceOf(InsufficientStockException.class)
                    .hasMessageContaining("재고 부족");
            server.verify();
        }

        @Test
        @DisplayName("결제 실패 시 PaymentFailedException 및 재고 복구 호출")
        void paymentFails() throws IOException {
            // MockRestServiceServer 대신 MockWebServer 사용: 경로별 스텁, 순서/매칭 이슈 제거
            MockWebServer productServer = new MockWebServer();
            MockWebServer paymentServer = new MockWebServer();
            try {
                productServer.setDispatcher(productDispatcher());
                paymentServer.setDispatcher(paymentDispatcher());
                productServer.start();
                paymentServer.start();

                String productBase = productServer.url("/").toString().replaceAll("/$", "");
                String paymentBase = paymentServer.url("/").toString().replaceAll("/$", "");
                RestTemplate rt = new RestTemplate();
                OrderService svc = new OrderService(orderRepository, rt, outboxService, productBase, paymentBase);

                assertThatThrownBy(() -> svc.createOrder(1L, 1L, 2, "CARD"))
                        .isInstanceOf(PaymentFailedException.class)
                        .hasMessageContaining("결제 실패");
            } finally {
                productServer.shutdown();
                paymentServer.shutdown();
            }
        }

        @Test
        @DisplayName("결제 성공 후 주문 저장 실패 시 Outbox에 보상 이벤트 발행")
        void orderSaveFailsThenOutboxPublished() {
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
                            "{\"success\":true,\"paymentId\":99,\"reason\":\"APPROVED\"}",
                            MediaType.APPLICATION_JSON));

            when(orderRepository.save(any(Order.class))).thenThrow(new RuntimeException("DB 저장 실패"));

            assertThatThrownBy(() -> orderService.createOrder(1L, 1L, 2, "CARD"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("DB 저장 실패");

            verify(outboxService).publishOrderSaveFailed(99L, 1L, 1L, 2);
            server.verify();
        }

        private Dispatcher productDispatcher() {
            return new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String path = request.getPath();
                    if ("GET".equals(request.getMethod()) && path != null && path.startsWith("/products/")) {
                        return json(200, "{\"id\":1,\"name\":\"A\",\"price\":10000,\"stockQuantity\":10}");
                    }
                    if ("POST".equals(request.getMethod()) && path != null && path.contains("/internal/stocks/reserve")) {
                        return json(200, "{\"success\":true,\"reason\":\"성공\",\"remainingStock\":8}");
                    }
                    if ("POST".equals(request.getMethod()) && path != null && path.contains("/internal/stocks/release")) {
                        return json(200, "{}");
                    }
                    return new MockResponse().setResponseCode(404);
                }
            };
        }

        private Dispatcher paymentDispatcher() {
            return new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String path = request.getPath();
                    if ("POST".equals(request.getMethod()) && path != null && path.startsWith("/payments")) {
                        return json(200, "{\"success\":false,\"paymentId\":null,\"reason\":\"결제 실패\"}");
                    }
                    return new MockResponse().setResponseCode(404);
                }
            };
        }

        private static MockResponse json(int code, String body) {
            return new MockResponse()
                    .setResponseCode(code)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body);
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
        @DisplayName("없으면 OrderNotFoundException")
        void notFound() {
            when(orderRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.getOrder(999L))
                    .isInstanceOf(OrderNotFoundException.class)
                    .hasMessageContaining("주문을 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("getOrdersByUser")
    class GetOrdersByUser {

        @Test
        @DisplayName("userId 기준 목록 반환")
        void success() {
            Order o1 = new Order(1L, 1L, 2, 20_000, OrderStatus.PAID);
            when(orderRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(o1));

            var result = orderService.getOrdersByUser(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getUserId()).isEqualTo(1L);
            assertThat(result.get(0).getProductId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("cancelOrder")
    class CancelOrder {

        @Test
        @DisplayName("PAID 주문 취소 시 결제 취소 + 재고 복구 + CANCELLED")
        void success() {
            Order order = new Order(1L, 1L, 2, 20_000, OrderStatus.PAID, 100L);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            server.expect(requestTo(PAYMENT_BASE + "/payments/100/cancel"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess());
            server.expect(requestTo(PRODUCT_BASE + "/internal/stocks/release"))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withSuccess());
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            Order result = orderService.cancelOrder(1L, 1L);

            assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            server.verify();
        }

        @Test
        @DisplayName("paymentId 없으면 OrderCannotBeCancelledException")
        void noPaymentId() {
            Order order = new Order(1L, 1L, 2, 20_000, OrderStatus.PAID);  // paymentId null
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L))
                    .isInstanceOf(OrderCannotBeCancelledException.class)
                    .hasMessageContaining("결제 정보가 없어");
        }

        @Test
        @DisplayName("이미 CANCELLED면 OrderCannotBeCancelledException")
        void alreadyCancelled() {
            Order order = new Order(1L, 1L, 2, 20_000, OrderStatus.CANCELLED, 100L);
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> orderService.cancelOrder(1L, 1L))
                    .isInstanceOf(OrderCannotBeCancelledException.class)
                    .hasMessageContaining("취소할 수 없는 주문");
        }
    }
}
