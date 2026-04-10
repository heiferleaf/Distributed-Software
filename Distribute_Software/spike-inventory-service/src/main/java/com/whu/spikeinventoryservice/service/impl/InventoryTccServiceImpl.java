package com.whu.spikeinventoryservice.service.impl;

import com.whu.spikeinventoryservice.entity.Inventory;
import com.whu.spikeinventoryservice.entity.TccTransactionLog;
import com.whu.spikeinventoryservice.entity.TccTransactionLogId;
import com.whu.spikeinventoryservice.repository.InventoryRepository;
import com.whu.spikeinventoryservice.repository.TccTransactionLogRepository;
import com.whu.spikeinventoryservice.service.InventoryTccService;
import io.seata.rm.tcc.api.BusinessActionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryTccServiceImpl implements InventoryTccService {

    private final InventoryRepository inventoryRepository;
    private final TccTransactionLogRepository tccTransactionLogRepository;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean prepare(BusinessActionContext actionContext, Long productId, Integer count) {
        String xid = actionContext.getXid();
        String branchId = String.valueOf(actionContext.getBranchId());
        log.info("库存TCC Try 阶段开始, xid: {}, branchId: {}, productId: {}, count: {}", xid, branchId, productId, count);

        // 幂等/防重检查
        TccTransactionLogId logId = new TccTransactionLogId(xid, branchId, "Try");
        if (tccTransactionLogRepository.existsById(logId)) {
            log.info("Try存在记录，库存阶段幂等放行");
            return true;
        }

        // 防悬挂: 如果Cancel比Try先到，已经留下了Cancel日志，这里直接抛出异常拒绝
        TccTransactionLogId cancelLogId = new TccTransactionLogId(xid, branchId, "Cancel");
        if (tccTransactionLogRepository.existsById(cancelLogId)) {
            throw new RuntimeException("检测到Cancel早于Try执行(防悬挂触发)，拒绝库存Try操作");
        }

        // 1. 使用Lua脚本对Redis缓存进行原子性的预扣减库存 (取代低效的互斥阻塞锁)
        String script = "if (redis.call('exists', KEYS[1]) == 1) then " +
                        "    local stock = tonumber(redis.call('get', KEYS[1])); " +
                        "    if (stock >= tonumber(ARGV[1])) then " +
                        "        redis.call('incrby', KEYS[1], -(tonumber(ARGV[1]))); " +
                        "        return 1; " +
                        "    end; " +
                        "end; " +
                        "return 0;";

        String redisStockKey = "seckill:stock:" + productId;
        Long result = stringRedisTemplate.execute(
            new org.springframework.data.redis.core.script.DefaultRedisScript<>(script, Long.class),
            java.util.Collections.singletonList(redisStockKey),
            String.valueOf(count)
        );

        if (result == null || result == 0L) {
            throw new RuntimeException("Redis缓存表明库存不足或未预热，预留失败");
        }
        log.info("Redis 缓存库存原子预扣减完成");

        // 2. 数据库悲观行级锁验证与持久化修改 (利用 DB 的互斥性保证落地安全)
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId);
        if (inventory == null) {
            throw new RuntimeException("商品不存在，库存预留失败");
        }
        if (inventory.getAvailableStock() < count) {
            throw new RuntimeException("可用库存不足，预留失败");
        }

        inventory.setAvailableStock(inventory.getAvailableStock() - count);
        inventory.setLockedStock(inventory.getLockedStock() + count);
        inventoryRepository.save(inventory);

        // 插入Try日志记录
        TccTransactionLog tryLog = new TccTransactionLog();
        tryLog.setTxId(xid);
        tryLog.setBranchId(branchId);
        tryLog.setActionType("Try");
        tryLog.setStatus(1);
        tccTransactionLogRepository.save(tryLog);

        log.info("库存TCC Try 操作完成, productId: {}", productId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean commit(BusinessActionContext actionContext) {
        String xid = actionContext.getXid();
        String branchId = String.valueOf(actionContext.getBranchId());
        log.info("库存TCC Confirm 阶段开始, xid: {}", xid);

        // 幂等判断
        TccTransactionLogId confirmLogId = new TccTransactionLogId(xid, branchId, "Confirm");
        if (tccTransactionLogRepository.existsById(confirmLogId)) {
            return true;
        }

        // Confirm 是空操作，因为真正扣除 locked_stock 应该在用户实际支付后。
        // 但我们需要保存 Confirm 的执行记录

        TccTransactionLog confirmLog = new TccTransactionLog();
        confirmLog.setTxId(xid);
        confirmLog.setBranchId(branchId);
        confirmLog.setActionType("Confirm");
        confirmLog.setStatus(1);
        tccTransactionLogRepository.save(confirmLog);

        log.info("库存TCC Confirm 空操作阶段完成");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollback(BusinessActionContext actionContext) {
        String xid = actionContext.getXid();
        String branchId = String.valueOf(actionContext.getBranchId());
        log.info("库存TCC Cancel 阶段开始, xid: {}", xid);

        // 幂等判断
        TccTransactionLogId cancelLogId = new TccTransactionLogId(xid, branchId, "Cancel");
        if (tccTransactionLogRepository.existsById(cancelLogId)) {
            return true;
        }

        // 写入Cancel日志 (防悬挂底座)
        TccTransactionLog cancelLog = new TccTransactionLog();
        cancelLog.setTxId(xid);
        cancelLog.setBranchId(branchId);
        cancelLog.setActionType("Cancel");
        cancelLog.setStatus(1);
        tccTransactionLogRepository.save(cancelLog);

        // 空回滚检查
        TccTransactionLogId tryLogId = new TccTransactionLogId(xid, branchId, "Try");
        if (!tccTransactionLogRepository.existsById(tryLogId)) {
            log.warn("库存TCC Cancel 发生空回滚保护(无需回滚业务数据，仅保存Cancel空防悬挂日志), xid: {}", xid);
            return true;
        }

        // 正常回滚: 获取锁后，释放 locked_stock -> available_stock，并把 Redis 缓存加回来
        Object productIdObj = actionContext.getActionContext("productId");
        Object countObj = actionContext.getActionContext("count");
        if (productIdObj == null || countObj == null) {
            log.error("库存回滚上下文中缺乏必要的参数，请检查 Seata 拦截映射");
            return true;
        }

        Long productId = Long.valueOf(productIdObj.toString());
        Integer count = Integer.valueOf(countObj.toString());

        // Cancel阶段无需使用全局互斥锁，依靠DB行级锁即可安全回滚
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId);
        if (inventory != null) {
            inventory.setAvailableStock(inventory.getAvailableStock() + count);
            inventory.setLockedStock(inventory.getLockedStock() - count);
            inventoryRepository.save(inventory);

            // 加回 Redis 缓存
            String redisStockKey = "seckill:stock:" + productId;
            stringRedisTemplate.opsForValue().increment(redisStockKey, count);
            log.info("Redis 缓存库存已加回: {}", count);
        }

        log.info("库存TCC Cancel 阶段完成, 释放锁定库存完成");
        return true;
    }
}
