package com.whu.hospitalregistrationpipeline.controller;

import com.alibaba.fastjson.JSON;
import com.whu.hospitalregistrationpipeline.entity.Registration;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

@RestController
@RequestMapping("/reg")
public class RegistrationController{
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    public void setKafkaTemplate(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * 单次挂号
     * 通过 Kafka 消息队列做异步的处理
     * 不是直接写数据库
     */
    @PostMapping("/create")
    public String createRegistration(@RequestParam Long patientId, @RequestParam Long doctorId) {
        System.out.println("hello world" + patientId + " " + doctorId);

        Registration reg = new Registration();
        // 使用时间戳+随机数模拟分布式环境下的业务流水号
        reg.setRegistrationId(System.currentTimeMillis() + new Random().nextInt(100));
        reg.setPatientId(patientId);
        reg.setDoctorId(doctorId);
        reg.setStatus(0); // 待支付

        // 物理动作：向 Kafka 的 "medical-reg" Topic 发送 JSON 字符串
        // Key 设为 patientId，确保同一个患者的消息进入同一个 Partition，保证顺序性
        kafkaTemplate.send("medical-reg", patientId.toString(), JSON.toJSONString(reg));

        return "挂号请求已受理，正在分库入库中... PatientID: " + patientId;
    }


}
