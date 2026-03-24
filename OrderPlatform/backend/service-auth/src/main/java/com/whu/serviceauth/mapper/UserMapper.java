package com.whu.serviceauth.mapper;

import com.whu.serviceauth.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {
    User selectById(@Param("id") Long id);
    User selectByUsername(@Param("username") String username);
    User selectByEmail(@Param("email") String email);
    User selectByPhone(@Param("phone") String phone);
    int insert(User user);
    int updateById(User user);
    int deleteById(@Param("id") Long id);
    User findActiveUserByUsername(@Param("username") String username);
}
