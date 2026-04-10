package com.whu.spikeproductservice.listener;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.whu.spikeproductservice.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CanalRabbitMQListener {

    private final StringRedisTemplate redisTemplate;

    /**
     * 并发旁路缓存模式 - Canel 订阅数据库变更后在MQ发出消息同步被当前服务监听到
     * 这里监听分片表 product_1, product_0，发生改变则异步删除 Redis
     */
    @RabbitListener(queues = RabbitMQConfig.CANAL_QUEUE)
    public void receiveCanalMessage(String message) {
        try {
            JSONObject jsonObject = JSON.parseObject(message);
            // 拿到事件类型与影响的物理表
            String type = jsonObject.getString("type");
            String table = jsonObject.getString("table");
            String database = jsonObject.getString("database");

            // 判断这是不是操作本服务的商品数据！因为 ShardingSphere 生成表所以是 product_X
            if ("spike_db".equals(database) && table != null && table.startsWith("product_")) {
                if ("UPDATE".equals(type) || "DELETE".equals(type)) {
                    // 获取受到影响的每一条数据
                    JSONArray dataList = jsonObject.getJSONArray("data");
                    for (int i = 0; i < dataList.size(); i++) {
                        JSONObject row = dataList.getJSONObject(i);
                        Long id = row.getLong("id");

                        // [数据一致性清理缓存] 不管是改了还是废了，去缓存里清理掉 ID的那个Key即可
                        String cacheKey = "product:item:" + id;
                        Boolean deleted = redisTemplate.delete(cacheKey);
                        log.info("==> [Canel+MQ监听到商品库变更] 物理表{}的行ID={}发起{}, 缓存{}已执行删除！", table, id, type, Boolean.TRUE.equals(deleted) ? "成功" : "无需删除（本地也不存在）");
                    }
                }
                // INSERT不需要在这个事件里补BloomFilter，在Service里已经插补过。但如果是后台批量导数据可以这里预留机制
            }
        } catch (Exception e) {
            log.error("==> Canel MQ 解析/执行报错拉胯，消息内容: {}", message, e);
        }
    }
}
