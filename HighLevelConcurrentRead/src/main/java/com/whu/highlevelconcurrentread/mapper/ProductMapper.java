package com.whu.highlevelconcurrentread.mapper;

import com.whu.highlevelconcurrentread.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ProductMapper{

    Optional<Product> findById(Long id);

    List<Product> findAll();

    /**
     * 修改商品信息（动态更新：只更新传入的非空字段）
     */
    @Update({
            "<script>",
            "UPDATE product",
            "<set>",
            "<if test='name != null'>name = #{name},</if>",
            "<if test='description != null'>description = #{description},</if>",
            "<if test='price != null'>price = #{price},</if>",
            "<if test='stock != null'>stock = #{stock},</if>",
            "</set>",
            "WHERE id = #{id}",
            "</script>"
    })
    void update(Product product);
}
