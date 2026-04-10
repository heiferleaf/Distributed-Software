package com.whu.spikeorderservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Data;
import java.sql.Timestamp;
import java.time.Instant;

@Entity
@Table(name = "tcc_transaction_logs")
@IdClass(TccTransactionLogId.class)
@Data
public class TccTransactionLog {
    @Id
    private String txId;
    @Id
    private String branchId;
    @Id
    private String actionType; // Try, Confirm, Cancel

    private Integer status; // 0-执行中 1-成功 2-失败
    private Timestamp createdAt = Timestamp.from(Instant.now());
}
