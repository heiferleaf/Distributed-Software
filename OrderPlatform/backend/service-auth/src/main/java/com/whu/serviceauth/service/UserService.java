package com.whu.serviceauth.service;

import com.whu.serviceauth.entity.User;
import com.whu.serviceauth.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    public Optional<User> getUserByUsername(String username) {
        return Optional.ofNullable(userMapper.selectByUsername(username));
    }

    public Optional<User> getActiveUserByUsername(String username) {
        return Optional.ofNullable(userMapper.findActiveUserByUsername(username));
    }

    public Optional<User> getUserByEmail(String email) {
        return Optional.ofNullable(userMapper.selectByEmail(email));
    }

    public Optional<User> getUserByPhone(String phone) {
        return Optional.ofNullable(userMapper.selectByPhone(phone));
    }

    public void createUser(User user) {
        userMapper.insert(user);
    }

    public void updateUser(User user) {
        userMapper.updateById(user);
    }

    public void deleteUser(Long id) {
        userMapper.deleteById(id);
    }
}
