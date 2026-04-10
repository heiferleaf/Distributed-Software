package com.whu.spikeorderservice.consumer;

import com.whu.spikeorderservice.controller.OrderController.OrderCreateMessage;
import com.whu.spikeorderservice.service.InventoryFeignService;
import com.whu.spikeorderservice.service.OrderTccService;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {

    private final OrderTccService orderTccService;
    private final InventoryFeignService inventoryFeignService;
    private final RedisTemplate<String, Object> redisTemplate;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = "order.create.queue"),
            exchange = @Exchange(name = "order.exchange"),
            key = "order.create"
    ))
    @GlobalTransactional(name = "create-order-tx", timeoutMills = 5000, rollbackFor = Exception.class)
    public void handleOrderCreate(OrderCreateMessage message) {
        log.info("接收到订单创建 MQ 消息，启动全局事务: {}", message);
        Long orderId = message.getOrderId();

        try {
            // 1. TCC 分支一：扣减/冻结远端库存 (RPC)
            boolean inventorySuccess = inventoryFeignService.prepareInventory(message.getProductId(), message.getAmount());
            if (!inventorySuccess) {
                throw new RuntimeException("库存冻结失败");
            }

            // [混沌测试注入点] 测试TCC回滚：库存成功扣除后，订单端突然发生局部异常崩溃
            if ("ORDER_FAIL".equals(message.getChaosType())) {
                log.error("【混沌测试】模拟订单服务在库存扣减后发生宕机或数据库异常！即将触发 Seata 全局回归 (Cancel)");
                throw new RuntimeException("【混沌测试】模拟本地订单创建落库失败");
            }

            // 2. TCC 分支二：创建本地订单
            boolean orderSuccess = orderTccService.prepareOrder(null, orderId, message.getUserId(), message.getProductId(), message.getAmount(), new BigDecimal("99.00"));
            if (!orderSuccess) {
                throw new RuntimeException("本地订单创建落库失败");
            }

            // 全部 TCC Try 完成并且没有异常，Seata TM 会在这之后自动触发 Confirm。
            // 此时，我们要把 Redis 的状态从 CREATING 改为 CREATED，以便放行前端的轮询请求
            redisTemplate.opsForValue().set("order:state:" + orderId, "CREATED");
            log.info("全局事务 Try 正常执行完成，发送 CREATED 状态至 Redis");

        } catch (Exception e) {
            log.error("处理订单创建业务出现异常，Seata 将触发全局回归", e);
            // 将其改为失败，前端拿到后直接显示报错
            redisTemplate.opsForValue().set("order:state:" + orderId, "FAILED");
            // 必须往外抛出异常，触发 Seata TM 发起 Cancel！！！
            throw e;
        }
    }
}
