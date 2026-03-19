package com.whu.highlevelconcurrentread.controller;

import com.whu.highlevelconcurrentread.entity.Product;
import com.whu.highlevelconcurrentread.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
public class ProductController{
    private final ProductService productService;

    public ProductController(@Qualifier("service") ProductService productService) {
        this.productService = productService;
    }

    @Value("${app.instance-id}")
    private String instanceId;

    /** 健康检查：验证哪个实例处理了请求 */
    @GetMapping("/health")
    public Map<String, String> health() {
        log.info("[实例:{}] 收到健康检查请求", instanceId);
        return Map.of(
                "status", "UP",
                "instance", instanceId
        );
    }

    /** 商品列表 */
    @GetMapping("/products")
    public List<Product> listProducts() {
        return productService.getAllProducts();
    }

    /** 商品详情 */
    @GetMapping("/products/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(product);
    }

    /**
     * PUT /api/products
     * 修改商品信息
     * 示例 JSON Body: { "id": 1, "price": 8999.00 }
     */
    @PutMapping
    public ResponseEntity<String> updateProduct(@RequestBody Product product) {
        if (product.getId() == null) {
            return ResponseEntity.badRequest().body("修改失败：商品 ID 不能为空");
        }

        // 核心：直接抛给 Service 更新数据库，然后瞬间返回！
        // 淘汰缓存的脏活累活，全靠后台的 Canal + RabbitMQ 默默完成。
        productService.updateProduct(product);

        log.info("API 收到修改请求，数据库更新指令已发出，快速响应前端。商品 ID: {}", product.getId());
        return ResponseEntity.ok("更新成功");
    }
}
