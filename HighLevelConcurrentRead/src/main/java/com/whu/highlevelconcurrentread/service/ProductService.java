package com.whu.highlevelconcurrentread.service;

import com.whu.highlevelconcurrentread.entity.Product;
import com.whu.highlevelconcurrentread.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service("service")
public class ProductService {
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.instance-id}")
    private String instanceId;

    private static final String PRODUCT_KEY = "product:";
    // 防止缓存穿透，空值设置
    private static final String NULL_VALUE  = "NULL";
    // 防止缓存击穿，设置锁
    private static final String LOCK_KEY  = "lock:product";

    public Product getProductById(Long id) {
        log.info("[实例:{}] 查询商品: {}", instanceId, id);

        // 1. 查询缓存GET
        String cacheKey = PRODUCT_KEY + id;
        Object value    = redisTemplate.opsForValue().get(cacheKey);

        // 缓存中存在数值
        if(value != null) {
            // 如果是为了防止穿透设置的空值
            if(NULL_VALUE.equals(value)) {
                log.debug("[实例:{}] 命中空值缓存，商品不存在: {}", instanceId, id);
                return null;
            }
            // 否则为有效值，直接返回
            return (Product) value; // 可以直接类型转换而不是反序列化，是因为在 redis 配置中，设置了将类信息存入
        }

        // 2. 当缓存不存在数据，可能是穿透或者击穿，查询数据库
        // 通过锁，保证下面的操作在某一时刻只有一个线程执行
        String lockKey = LOCK_KEY + id;
        // 因为redis是单线程执行，所以下面的操作具有原子性，如果原本不存在数据，设置数据
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
        if(locked) {
            // 双重检查，因为其他线程可能已经把数据写回缓存
            try {
                value = redisTemplate.opsForValue().get(cacheKey);
                if (value != null) {
                    if (NULL_VALUE.equals(value)) {
                        log.debug("[实例:{}] 命中空值缓存，商品不存在: {}", instanceId, id);
                        return null;
                    } else {
                        return (Product) value;
                    }
                }
                // 第一个走数据库查询的线程
                Optional<Product> product = productMapper.findById(id);
                // 防止雪崩，设置随机的过期时间
                product.ifPresentOrElse((p) -> redisTemplate.opsForValue().set(cacheKey, p, 30 + new Random().nextInt(10), TimeUnit.MINUTES)
                        , () -> redisTemplate.opsForValue().set(cacheKey, NULL_VALUE, 30 + new Random().nextInt(10), TimeUnit.MINUTES));

                return product.orElse(null);
            } finally {
                redisTemplate.delete(lockKey);
            }
        } else {
            log.debug("[实例:{}] 等待锁，重试查询: {}", instanceId, id);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return getProductById(id); // 递归重试
        }
    }

    // ==================== 查询全部商品 ====================
    public List<Product> getAllProducts() {
        log.info("[实例:{}] 查询全部商品", instanceId);
        String cacheKey = "products:all";

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return (List<Product>) cached;
        }

        List<Product> products = productMapper.findAll();
        // 列表缓存时间短，数据变化敏感
        redisTemplate.opsForValue().set(cacheKey, products, 5, TimeUnit.SECONDS);
        return products;
    }

    public void updateProduct(Product product) {
        log.info("[实例:{}] 修改商品信息，进入数据库更新流程: {}", instanceId, product.getId());

        // 【核心准则】：只更新数据库！不要在这里写任何 redisTemplate.delete()！
        // 只要数据库更新成功，MySQL 就会产生 Binlog
        // Canal 就会捕获 -> 发到 MQ -> Listener 异步删除对应缓存
        productMapper.update(product);
    }
}
