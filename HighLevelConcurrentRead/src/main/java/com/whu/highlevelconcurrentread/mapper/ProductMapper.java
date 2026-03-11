package com.whu.highlevelconcurrentread.mapper;

import com.whu.highlevelconcurrentread.entity.Product;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ProductMapper{

    Optional<Product> findById(Long id);

    List<Product> findAll();

}
