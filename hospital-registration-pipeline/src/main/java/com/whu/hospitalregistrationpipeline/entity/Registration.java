package com.whu.hospitalregistrationpipeline.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Registration {
    private Long            id;
    private Long            registrationId;
    private Long            patientId;
    private Long            doctorId;
    private Integer         status;
    private LocalDateTime   createTime;
}
