package com.whu.hospitalregistrationpipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication
@EnableKafka
public class HospitalRegistrationPipelineApplication{

    public static void main(String[] args) {
        SpringApplication.run(HospitalRegistrationPipelineApplication.class, args);
    }

}
