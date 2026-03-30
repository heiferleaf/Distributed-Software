package com.whu.hospitalregistrationpipeline.consumer;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.fastjson2.JSON;
import com.whu.hospitalregistrationpipeline.entity.Registration;
import com.whu.hospitalregistrationpipeline.mapper.RegistrationMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class RegistrationConsumer {
    private RegistrationMapper registrationMapper;

    @Autowired
    public void setRegistrationMapper(RegistrationMapper registrationMapper) {
        this.registrationMapper = registrationMapper;
    }

    // 1. 幂等性校验（状态机判断，订单号判断）
    // 2. 提交模式，at-least-once，做完在提交，如果提交丢失，就会多次执行
    @KafkaListener(topics = "medical-reg", groupId = "med-group", concurrency = "3")
    @SentinelResource(value = "processRegistration", blockHandler = "handleFlowBlock", fallback = "handlefallBack")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        System.out.println("Thread Name: " + Thread.currentThread().getName()
                + " | Partition ID: " + record.partition()
                + " | Key: " + record.key()
                + " | Value: " + record.value());

        // 拿到注册对象
        Registration reg = JSON.parseObject(record.value(), Registration.class);

        // 分库分表写入
        registrationMapper.insertRegistration(reg);

        // 手动确认，对应 at-least-once
        ack.acknowledge();
        System.out.println("数据库写入成功: PatientID " + reg.getPatientId());
    }

    // 1. 限流后的处理逻辑，必须是public
    // 参数完全一样，多一个接收的异常
    public void handleFlowBlock(ConsumerRecord<String, String> record, Acknowledgment ack, BlockException ex) {
        // 限流就不需要ack
        System.err.println("[Sentinel 可视化告警] 触发限流，QPS 过高，消息已入站缓冲。" + ex.getMessage());
    }
}
