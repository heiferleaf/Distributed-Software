package com.whu.spikeproductservice.config;

import com.whu.spikeproductservice.entity.ProductEntity;
import com.whu.spikeproductservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.CommandLineRunner;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class BloomFilterConfig {

    private final RedissonClient redissonClient;
    private final ProductRepository productRepository;

    @Bean
    public RBloomFilter<Long> productBloomFilter() {
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter("product:bloom:filter");
        // 初始化布隆过滤器，预计插入100万数据，容错率0.01
        // 如果已经初始化过了就不重复初始化（在集群环境下可能会竞争，更严谨可用分布式锁处理或者配置里保证幂等）
        if (!bloomFilter.isExists()) {
            bloomFilter.tryInit(1000000L, 0.01);
        }
        return bloomFilter;
    }

    @Bean
    public CommandLineRunner initBloomFilterData(RBloomFilter<Long> productBloomFilter) {
        return args -> {
            // 系统启动时，若过滤率未加载，则将数据库中现有商品的ID全量（或增量）推送到布隆过滤器中防穿透
            // （正常的大表应分页分批加载，此处作简易化处理，并认为商品不是天文级数量）
            if (productBloomFilter.count() == 0) {
                log.info("==> 开始初始化化布隆过滤器产品ID缓存...");
                try {
                    productRepository.findAll().stream()
                            .map(ProductEntity::getId)
                            .forEach(productBloomFilter::add);
                    log.info("==> 布隆过滤器初始化完成！当前数量: {}", productBloomFilter.count());
                } catch (Exception e) {
                   log.warn("==> 首次启动若 ShardingSphere 尚未构建完全，忽略此警告", e);
                }
            }
        };
    }
}
