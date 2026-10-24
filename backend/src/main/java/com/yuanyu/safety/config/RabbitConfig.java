package com.yuanyu.safety.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * RabbitMQ 告警事件拓扑：算法服务 -> exchange -> queue -> 业务服务。
 * 仅在 profile=mq 时启用，默认走 HTTP 直投，保证本地无中间件也能运行。
 */
@Configuration
@Profile("mq")
public class RabbitConfig {

    public static final String EXCHANGE = "yuanyu.alarm.exchange";
    public static final String QUEUE = "yuanyu.alarm.queue";
    public static final String ROUTING_KEY = "alarm.event";

    @Bean
    public Queue alarmQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public TopicExchange alarmExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Binding alarmBinding(Queue alarmQueue, TopicExchange alarmExchange) {
        return BindingBuilder.bind(alarmQueue).to(alarmExchange).with(ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        // 复用全局 ObjectMapper，保证 yyyy-MM-dd HH:mm:ss 时间格式与 HTTP 接口一致
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
