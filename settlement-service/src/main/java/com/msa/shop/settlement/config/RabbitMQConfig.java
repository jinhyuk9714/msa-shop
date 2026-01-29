package com.msa.shop.settlement.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * payment-service의 결제 완료 이벤트 구독용 Queue·Binding.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_PAYMENT_EVENTS = "payment.events";
    public static final String QUEUE_PAYMENT_COMPLETED = "settlement.payment.completed";
    public static final String ROUTING_KEY_PAYMENT_COMPLETED = "payment.completed";

    @Bean
    Queue paymentCompletedQueue() {
        return new Queue(QUEUE_PAYMENT_COMPLETED, true);
    }

    @Bean
    TopicExchange paymentEventsExchange() {
        return new TopicExchange(EXCHANGE_PAYMENT_EVENTS, true, false);
    }

    @Bean
    Binding paymentCompletedBinding(Queue paymentCompletedQueue, TopicExchange paymentEventsExchange) {
        return BindingBuilder.bind(paymentCompletedQueue).to(paymentEventsExchange).with(ROUTING_KEY_PAYMENT_COMPLETED);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }
}
