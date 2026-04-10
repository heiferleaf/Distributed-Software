package com.whu.spikeproductservice.service;

import com.alibaba.fastjson2.JSON;
import com.whu.spikeproductservice.entity.ProductEntity;
import com.whu.spikeproductservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final RBloomFilter<Long> productBloomFilter;

    private static final String PRODUCT_CACHE_PREFIX = "product:item:";
    private static final String PRODUCT_LOCK_PREFIX = "product:lock:";
    private static final String NULL_IDENTIFIER = "NULL_VAL";

    /**
     * 高并发下的防穿透、防雪崩、防击穿读取
     */
    public ProductEntity getProductSafely(Long id) {
        // [防穿透]：1. 首先校验布隆过滤器（如果Bloom里面都没有，那肯定没这商品，阻断海量请求直接落库）
        if (!productBloomFilter.contains(id)) {
            log.warn("==> 布隆过滤器拦截穿透请求 (商品不存在于布隆指纹中): ID={}", id);
            return null;
        }

        String cacheKey = PRODUCT_CACHE_PREFIX + id;
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);

        // 如果缓存命中了商品信息，直接返回
        if (cachedValue != null) {
            // [防穿透]：缓存空值
            if (NULL_IDENTIFIER.equals(cachedValue)) {
                return null;
            }
            return JSON.parseObject(cachedValue, ProductEntity.class);
        }

        // 如果没拿到，使用分布式锁保障数据库的压力。保证同一时刻相同的一个ID只放行一个请求去DB，其他线程自旋等待
        // [防击穿]：加锁读库
        RLock lock = redissonClient.getLock(PRODUCT_LOCK_PREFIX + id);
        try {
            boolean isLock = lock.tryLock(5, 5, TimeUnit.SECONDS); // 尝试等待5s内加锁，加到了5s后没释放自动放弃(死锁防止)
            if (isLock) {
                // 拿到锁的线程进行双重检查 (Double-Check)
                cachedValue = redisTemplate.opsForValue().get(cacheKey);
                if (cachedValue != null) {
                    if (NULL_IDENTIFIER.equals(cachedValue)) return null;
                    return JSON.parseObject(cachedValue, ProductEntity.class);
                }

                log.info("==> Redis未命中，查库: ID={}", id);
                Optional<ProductEntity> productOpt = productRepository.findById(id);

                if (productOpt.isPresent()) {
                    ProductEntity product = productOpt.get();
                    // [防雪崩]：写入缓存时设置基础时间(例如1小时) + 分散随机时间，避免热点大批同时失效
                    int randomMinutes = new Random().nextInt(30); // 0-30分钟随机量
                    long expireSeconds = 60 * 60 + (randomMinutes * 60L);

                    redisTemplate.opsForValue().set(cacheKey, JSON.toJSONString(product), expireSeconds, TimeUnit.SECONDS);
                    return product;
                } else {
                    // [防穿透]：走到数据库依然是空（可能Bloom误判），那就在缓存放一个短暂空对象（五分钟即可）
                    redisTemplate.opsForValue().set(cacheKey, NULL_IDENTIFIER, 5, TimeUnit.MINUTES);
                    return null;
                }
            } else {
                // 没拿到锁的线程在这里自旋并等待获取锁的进程写进去的缓存
                Thread.sleep(50);
                return getProductSafely(id); // 稍等后再次重试自身方法直接走向拿缓存
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("线程获取互斥锁中断", e);
        } finally {
            if (lock.isHeldByCurrentThread()) { // 如果锁还归当前线程自己拿着，自己清掉
                lock.unlock();
            }
        }
    }

    /**
     * 游标查询商品列表，避免深层 offset 问题
     */
    public List<ProductEntity> listProductsByCursor(Long cursorId, int limit) {
        // 获取指定游标后开始按步长的 N个商品数据
        PageRequest pageRequest = PageRequest.of(0, limit);
        return productRepository.findByIdGreaterThanOrderByIdAsc(cursorId, pageRequest);
    }

    /**
     * 写操作：基于数据一致性。保存/更新至DB即可，因为 Canel 配合 RabbitMQ 会异步去删光 Redis 里对应的缓存（旁路方案）
     */
    public ProductEntity saveOrUpdateProduct(ProductEntity product) {
        ProductEntity saved = productRepository.save(product);
        log.info("==> Saved product ID in JPA Entity: {}", saved.getId());

        // [防穿透补充] 如果是新的商品被发布，及时增加布隆过滤器指纹
        if (saved.getId() != null && !productBloomFilter.contains(saved.getId())) {
            productBloomFilter.add(saved.getId());
            log.info("==> 添加到布隆过滤器完成: ID={}", saved.getId());
        }
        return saved;
    }
}
