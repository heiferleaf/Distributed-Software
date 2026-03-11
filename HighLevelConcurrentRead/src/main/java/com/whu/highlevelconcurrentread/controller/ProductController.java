package com.whu.highlevelconcurrentread.controller;

import com.whu.highlevelconcurrentread.entity.Product;
import com.whu.highlevelconcurrentread.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
