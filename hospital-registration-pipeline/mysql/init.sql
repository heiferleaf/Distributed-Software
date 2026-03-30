CREATE TABLE IF NOT EXISTS t_registration_0 (
      id BIGINT NOT NULL COMMENT '逻辑主键',
      registration_id BIGINT NOT NULL COMMENT '挂号单流水号',
      patient_id BIGINT NOT NULL COMMENT '患者ID (分库键)',
      doctor_id BIGINT NOT NULL COMMENT '医生ID',
      status INT DEFAULT 0 COMMENT '状态: 0-待支付, 1-完成, 2-已取消',
      create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_registration_1 (
    id BIGINT NOT NULL COMMENT '逻辑主键',
    registration_id BIGINT NOT NULL COMMENT '挂号单流水号',
    patient_id BIGINT NOT NULL COMMENT '患者ID (分库键)',
    doctor_id BIGINT NOT NULL COMMENT '医生ID',
    status INT DEFAULT 0 COMMENT '状态: 0-待支付, 1-完成, 2-已取消',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);