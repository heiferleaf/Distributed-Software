package com.whu.spikeorderservice.service;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("inventory-service")
public interface InventoryFeignService {

    @PostMapping("/inventory/try")
    boolean prepareInventory(@RequestParam("productId") Long productId,
                           @RequestParam("count") Integer count);
}
