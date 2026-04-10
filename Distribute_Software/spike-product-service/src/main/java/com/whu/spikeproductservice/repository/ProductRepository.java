package com.whu.spikeproductservice.repository;

import com.whu.spikeproductservice.entity.ProductEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    // 基于游标查询的深分页替代方案：查找 ID 大于给定 cursor 的商品（升序排列，查前N条）
    List<ProductEntity> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);
}
