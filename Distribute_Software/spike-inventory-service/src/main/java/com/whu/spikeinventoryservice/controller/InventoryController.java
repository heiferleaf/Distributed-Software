package com.whu.spikeinventoryservice.controller;

import com.whu.spikeinventoryservice.entity.Inventory;
import com.whu.spikeinventoryservice.repository.InventoryRepository;
import com.whu.spikeinventoryservice.service.InventoryTccService;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
@AllArgsConstructor
public class InventoryController {

    private final InventoryRepository inventoryRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final InventoryTccService inventoryTccService;

    /**
     * 提供给商品服务（Product Service）在新建商品时调用
     */
    @PostMapping("/init")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> initInventory(@RequestParam("productId") Long productId,
                                           @RequestParam("stock") Integer stock) {

        Inventory inventory = inventoryRepository.findByProductId(productId);
        if (inventory != null) {
            return ResponseEntity.badRequest().body("该商品库存已存在");
        }

        inventory = new Inventory();
        inventory.setProductId(productId);
        inventory.setTotalStock(stock);
        inventory.setAvailableStock(stock);
        inventory.setLockedStock(0);
        inventoryRepository.save(inventory);

        // 强行写入 Redis 缓存预热，供高并发场景 Lua 扣减使用
        stringRedisTemplate.opsForValue().set("seckill:stock:" + productId, String.valueOf(stock));

        return ResponseEntity.ok("库存初始化成功并缓存预热完成");
    }

    @GetMapping("/{productId}")
    public ResponseEntity<?> getInventory(@PathVariable("productId") Long productId) {
        Inventory inventory = inventoryRepository.findByProductId(productId);
        if (inventory == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(inventory);
    }

    /**
     * 提供给商品服务在系统管理中用来动态调整库存 (加减量)
     * 这里加锁防止超发与后台增加同时产生不可预料的数据冲突
     */
    @PostMapping("/addStock")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> addStock(@RequestParam("productId") Long productId,
                                      @RequestParam("delta") Integer delta) {
        Inventory inventory = inventoryRepository.findByProductIdForUpdate(productId);
        if (inventory == null) {
            return ResponseEntity.badRequest().body("库存不存在");
        }

        inventory.setTotalStock(inventory.getTotalStock() + delta);
        inventory.setAvailableStock(inventory.getAvailableStock() + delta);
        inventoryRepository.save(inventory);

        // 同步修改Redis
        stringRedisTemplate.opsForValue().increment("seckill:stock:" + productId, delta);

        return ResponseEntity.ok("库存调整成功");
    }

    /**
     * 提供给订单服务TCC Try阶段调用的接口
     */
    @PostMapping("/try")
    public boolean prepareInventory(@RequestParam("productId") Long productId,
                                    @RequestParam("count") Integer count) {
        // Seata 拦截器会自动拦截此调用，并向 inventoryTccService 注入 GlobalTransaction Context
        return inventoryTccService.prepare(null, productId, count);
    }
}
