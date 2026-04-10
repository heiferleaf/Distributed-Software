package com.whu.spikeorderservice.controller;


import com.whu.spikeorderservice.service.InventoryFeignService;
import com.whu.spikeorderservice.util.SnowFlakeIDGenerator;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/order")
@AllArgsConstructor
public class OrderController {

    private final InventoryFeignService inventoryFeignService;
    private final RabbitTemplate rabbitTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SnowFlakeIDGenerator snowFlakeIDGenerator;

    @PostMapping("/create")
    public ResponseEntity<?> createOrder(@RequestParam("userId") Long userId,
                                         @RequestParam("productId") Long productId,
                                         @RequestParam("amount") Integer amount,
                                         @RequestParam(value = "chaosType", required = false) String chaosType) {

        // 1. 使用雪花算法生成全局唯一订单ID
        Long orderId = snowFlakeIDGenerator.nextId();

        // 2. 将订单状态写入Redis，标记为"CREATING"（创建中），设置过期时间防止一直占用
        redisTemplate.opsForValue().set("order:state:" + orderId, "CREATING", 30, java.util.concurrent.TimeUnit.MINUTES);

        // 3. 发送MQ消息，异步解耦
        OrderCreateMessage message = new OrderCreateMessage(orderId, userId, productId, amount, chaosType);
        rabbitTemplate.convertAndSend("order.exchange", "order.create", message);

        // 4. 立即向前端返回订单ID，前端通过此ID轮询订单状态
        return ResponseEntity.ok(java.util.Map.of("orderId", orderId, "message", "接单成功，正在排队..."));
    }

    @GetMapping("/status/{orderId}")
    public ResponseEntity<?> getOrderStatus(@PathVariable("orderId") Long orderId) {
        // 前端轮询接口
        Object status = redisTemplate.opsForValue().get("order:state:" + orderId);

        if (status == null) {
             return ResponseEntity.badRequest().body("订单不存在或已过期");
        }

        if ("CREATED".equals(status)) {
            // TODO: 从数据库中查询完整的订单详情并返回
            // Order order = orderService.getById(orderId);
            // return ResponseEntity.ok(order);
            return ResponseEntity.ok("{\"status\": \"CREATED\", \"message\": \"订单创建成功，请进入支付页面\", \"orderId\": \"" + orderId + "\"}");
        } else if ("FAILED".equals(status)) {
            return ResponseEntity.status(500).body("{\"status\": \"FAILED\", \"message\": \"订单创建失败，库存不足或系统异常\"}");
        }

        // 仍在创建中
        return ResponseEntity.ok("{\"status\": \"CREATING\", \"message\": \"订单正在排队处理中...\"}");
    }

    @Data
    @AllArgsConstructor
    public static class OrderCreateMessage {
        private Long orderId;
        private Long userId;
        private Long productId;
        private Integer amount;
        private String chaosType;
    }
}
