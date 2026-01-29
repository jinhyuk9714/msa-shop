package com.msa.shop.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 결제 완료 이벤트 발행용 Exchange. settlement-service가 동일 이름 exchange에 바인딩해 구독.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_PAYMENT_EVENTS = "payment.events";
    public static final String ROUTING_KEY_PAYMENT_COMPLETED = "payment.completed";

    @Bean
    TopicExchange paymentEventsExchange() {
        return new TopicExchange(EXCHANGE_PAYMENT_EVENTS, true, false);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }
}
