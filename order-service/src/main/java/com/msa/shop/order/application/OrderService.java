package com.msa.shop.order.application;

import com.msa.shop.order.domain.Order;
import com.msa.shop.order.domain.OrderRepository;
import com.msa.shop.order.domain.OrderStatus;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestTemplate restTemplate;
    private final String productServiceBaseUrl;
    private final String paymentServiceBaseUrl;

    public OrderService(
            OrderRepository orderRepository,
            RestTemplate restTemplate,
            @Value("${product-service.base-url}") String productServiceBaseUrl,
            @Value("${payment-service.base-url}") String paymentServiceBaseUrl
    ) {
        this.orderRepository = orderRepository;
        this.restTemplate = restTemplate;
        this.productServiceBaseUrl = productServiceBaseUrl;
        this.paymentServiceBaseUrl = paymentServiceBaseUrl;
    }

    @Transactional
    public Order createOrder(Long userId, Long productId, int quantity, String paymentMethod) {
        ProductResponse product = getProduct(productId);
        int totalAmount = product.price() * quantity;

        ReserveStockResponse stockResponse = reserveStock(userId, productId, quantity);
        if (!stockResponse.success()) {
            throw new IllegalStateException("재고 부족: " + stockResponse.reason());
        }

        PaymentResponse paymentResponse = requestPayment(userId, totalAmount, paymentMethod);
        if (!paymentResponse.success()) {
            throw new IllegalStateException("결제 실패: " + paymentResponse.reason());
        }

        Order order = new Order(userId, productId, quantity, totalAmount, OrderStatus.PAID);
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다. id=" + id));
    }

    @Retry(name = "productService")
    @CircuitBreaker(name = "productService")
    ProductResponse getProduct(Long productId) {
        String url = productServiceBaseUrl + "/products/" + productId;
        ResponseEntity<ProductResponse> response =
                restTemplate.getForEntity(url, ProductResponse.class);
        return response.getBody();
    }

    @Retry(name = "productService")
    @CircuitBreaker(name = "productService")
    ReserveStockResponse reserveStock(Long userId, Long productId, int quantity) {
        String url = productServiceBaseUrl + "/internal/stocks/reserve";
        Map<String, Object> body = Map.of(
                "userId", userId,
                "productId", productId,
                "quantity", quantity
        );
        ResponseEntity<ReserveStockResponse> response =
                restTemplate.postForEntity(url, body, ReserveStockResponse.class);
        return response.getBody();
    }

    @Retry(name = "paymentService")
    @CircuitBreaker(name = "paymentService")
    PaymentResponse requestPayment(Long userId, int amount, String paymentMethod) {
        String url = paymentServiceBaseUrl + "/payments";
        Map<String, Object> body = Map.of(
                "userId", userId,
                "amount", amount,
                "paymentMethod", paymentMethod
        );
        ResponseEntity<PaymentResponse> response =
                restTemplate.postForEntity(url, body, PaymentResponse.class);
        return response.getBody();
    }

    public record ProductResponse(Long id, String name, int price, int stockQuantity) {}

    public record ReserveStockResponse(boolean success, String reason, int remainingStock) {}

    public record PaymentResponse(boolean success, Long paymentId, String reason) {}
}

