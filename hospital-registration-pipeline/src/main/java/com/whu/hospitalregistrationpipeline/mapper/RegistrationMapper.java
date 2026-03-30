package com.whu.hospitalregistrationpipeline.mapper;

import com.whu.hospitalregistrationpipeline.entity.Registration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RegistrationMapper{
    int insertRegistration(Registration registration);

    Registration selectRegistrationById(@Param("patientId") Long patientId);
}
