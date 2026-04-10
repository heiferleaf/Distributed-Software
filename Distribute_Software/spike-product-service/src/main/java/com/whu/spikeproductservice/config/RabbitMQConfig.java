package com.whu.spikeproductservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String CANAL_EXCHANGE = "canal.exchange";
    public static final String CANAL_QUEUE = "canal.queue";
    public static final String CANAL_ROUTING_KEY = "canal.routing.key";

    @Bean
    public TopicExchange canalExchange() {
        return new TopicExchange(CANAL_EXCHANGE, true, false);
    }

    @Bean
    public Queue canalQueue() {
        // durable:true 消息持久化
        return QueueBuilder.durable(CANAL_QUEUE).build();
    }

    @Bean
    public Binding canalBinding(Queue canalQueue, TopicExchange canalExchange) {
        return BindingBuilder.bind(canalQueue).to(canalExchange).with(CANAL_ROUTING_KEY);
    }
}
