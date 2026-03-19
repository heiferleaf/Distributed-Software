package com.whu.highlevelconcurrentread.listener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.whu.highlevelconcurrentread.dto.CanalMessage;
import com.whu.highlevelconcurrentread.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class CanalCacheSyncListener {
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "canal_shop_queue", durable = "true"),   // 在 MQ 服务器中，声明一个对列，接收消息，持久化存储到磁盘中
            exchange = @Exchange(value = "canal.exchange", type = "direct"),  // 在 MQ 服务器中，连接canal_exchange这个消息中心，指定连接方式是直连，所以只有消息邮编和指定接收邮编完全一致，才会被放到消息队列中
            key = "example"
    ))
    public void processProductChange(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        log.info("receive message with deliveryTag {}", deliveryTag);
        try {
            String jsonStr = new String(message.getBody(), StandardCharsets.UTF_8);

            if(jsonStr.isBlank()) return;

            CanalMessage<Product> canalMessage = objectMapper.readValue(jsonStr, new TypeReference<CanalMessage<Product>>(){});
            if(canalMessage == null) return;

            if("shop".equals(canalMessage.getDatabase()) && "product".equals(canalMessage.getTable())) {
                String type = canalMessage.getType();
                if("UPDATE".equals(type) || "DELETE".equals(type)) {
                    for(Product product : canalMessage.getData()) {
                        String productCacheKey = "product:" + product.getId();
                        redisTemplate.delete(productCacheKey);
                        log.info("异步清理缓存");
                    }
                    String allProductCacheKey = "products:all";
                    redisTemplate.delete(allProductCacheKey);
                    log.info("异步清理全局商品列表缓存: products:all");
                }
            }
            channel.basicAck(deliveryTag, true);
        } catch (Exception e) {
            log.error(e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
