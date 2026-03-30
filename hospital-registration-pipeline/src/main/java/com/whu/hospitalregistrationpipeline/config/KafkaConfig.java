package com.whu.hospitalregistrationpipeline.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaOperations<Object, Object> template) {

        // 1. 死信队列 - 当一条消息执行三次都失败，恢复器会把消息重新发送到死信队列，对应一个 ‘medical-reg.DLT’ 的Topic
        ConsumerRecordRecoverer recordRecoverer = new DeadLetterPublishingRecoverer(template);
        // 2. BackOff 避退算法：每间隔2秒重试，最多重试3次
        FixedBackOff backOff = new FixedBackOff(2000L, 3);
        // 3. 构造处理器，把上面配置的避退方案，和恢复方案，进行应用
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recordRecoverer, backOff);
        // 4. 构建重试黑名单，如果是发生了某种异常，导致消费失败，直接进行恢复方法，不重试
        errorHandler.addNotRetryableExceptions(NullPointerException.class);
        return errorHandler;
    }
}
