package com.whu.spikeproductservice.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "inventory-service")
public interface InventoryFeignService {

    @PostMapping("/inventory/init")
    ResponseEntity<?> initInventory(@RequestParam("productId") Long productId,
                                    @RequestParam("stock") Integer stock);

    @PostMapping("/inventory/addStock")
    ResponseEntity<?> addStock(@RequestParam("productId") Long productId,
                               @RequestParam("delta") Integer delta);
}
