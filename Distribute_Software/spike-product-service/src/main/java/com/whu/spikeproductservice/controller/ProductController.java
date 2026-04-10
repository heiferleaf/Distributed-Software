package com.whu.spikeproductservice.controller;

import com.whu.spikeproductservice.entity.ProductEntity;
import com.whu.spikeproductservice.service.InventoryFeignService;
import com.whu.spikeproductservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final InventoryFeignService inventoryFeignService;

    /**
     * 高并发读取：依靠布隆及Redis缓存返回商品信息。如果没过网关透传则随便谁都能查。
     * 用户可以查
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getProduct(@PathVariable("id") Long id) {
        ProductEntity productSafely = productService.getProductSafely(id);
        if (productSafely == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "未找到该商品或防击穿已被拦截"));
        }
        return ResponseEntity.ok(Map.of("code", 200, "data", productSafely));
    }

    /**
     * 解决深分页的列表查询方法（游标）：使用大于 ID 返回一页数量内容，游标最初由0给
     * 用户可以查
     */
    @GetMapping("/list")
    public ResponseEntity<?> listProducts(@RequestParam(defaultValue = "0") Long cursor,
                                          @RequestParam(defaultValue = "10") Integer limit) {
        List<ProductEntity> result = productService.listProductsByCursor(cursor, limit);
        Long nextCursor = result.isEmpty() ? cursor : result.get(result.size() - 1).getId();
        return ResponseEntity.ok(Map.of(
            "code", 200,
            "data", result,
            "next_cursor", nextCursor != null ? nextCursor.toString() : "0",
            "has_more", result.size() == limit
        ));
    }

    /**
     * 通过管理员权限增加新商品（商品分表写入） 必须有管理员头权限
     */
    @PostMapping("/admin/add")
    public ResponseEntity<?> addProduct(@RequestHeader(value = "X-User-Role", required = false) String role,
                                        @RequestBody ProductEntity product) {
        // [权限校验] 如果不是上游网关透传过来的 ADMIN 角色不让改！
        if (!"ADMIN".equals(role)) {
            log.warn("拦截了不当写入商品的请求角色: {}", role);
            return ResponseEntity.status(403).body("仅限大促管理员发布商品");
        }

        try {
            // 存入数据库
            ProductEntity savedProduct = productService.saveOrUpdateProduct(product);

            // 初始化库存，存入分库分表的 inventory 及 Redis 缓存预热
            inventoryFeignService.initInventory(savedProduct.getId(), product.getStock());

            return ResponseEntity.ok(Map.of("message", "商品发布与库存预热成功", "productId", savedProduct.getId()));
        } catch (Exception e) {
            log.error("商品发布失败", e);
            return ResponseEntity.status(500).body("商品发布失败: " + e.getMessage());
        }
    }

    /**
     * 通过管理员权限更新（直接写入数据库）触发 Canal-MQ 删除缓存
     */
    @PutMapping("/admin/update/{id}")
    public ResponseEntity<?> updateProduct(@RequestHeader(value = "X-User-Role", required = false) String role,
                                           @PathVariable("id") Long id,
                                           @RequestBody ProductEntity product) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "权限不足以更新商品。"));
        }

        ProductEntity existingProduct = productService.getProductSafely(id);
        if (existingProduct == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "商品不存在"));
        }

        if (product.getName() != null) existingProduct.setName(product.getName());
        if (product.getPrice() != null) existingProduct.setPrice(product.getPrice());
        if (product.getDescription() != null) existingProduct.setDescription(product.getDescription());

        ProductEntity saved = productService.saveOrUpdateProduct(existingProduct);

        Integer stockDelta = product.getStock();
        // 显式调用库存管理接口增减可用和总库存
        if (stockDelta != null && stockDelta != 0) {
            try {
                inventoryFeignService.addStock(id, stockDelta);
            } catch (Exception e) {
                log.error("调用库存服务调整库存时失败", e);
                return ResponseEntity.status(500).body(Map.of("error", "商品基本信息修改成功，但库存服务通信失败"));
            }
        }
        return ResponseEntity.ok(Map.of("message", "商品修改成功，已异步处理旧缓存", "data", saved));
    }
}
