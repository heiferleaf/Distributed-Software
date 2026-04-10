package com.whu.spikeorderservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import java.math.BigDecimal;

@Entity
@Table(name = "orders")
@Data
public class Order {

    @Id
    private Long orderId;

    private Long userId;

    private Long productId;

    private Integer amount;

    private BigDecimal totalPrice;

    private Integer status; // 0-INIT, 1-CREATED, 2-CANCELLED
}
