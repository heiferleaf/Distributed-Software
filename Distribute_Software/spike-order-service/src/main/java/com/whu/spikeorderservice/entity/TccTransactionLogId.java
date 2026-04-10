package com.whu.spikeorderservice.entity;

import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TccTransactionLogId implements Serializable {
    private String txId;
    private String branchId;
    private String actionType;
}
