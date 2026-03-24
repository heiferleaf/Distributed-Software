package com.whu.serviceauth.service;

import com.whu.serviceauth.entity.User;
import com.whu.serviceauth.mapper.UserMapper;
import com.whu.serviceauth.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    public User register(String username, String rawPassword, String email, String phone) {
        if (userMapper.selectByUsername(username) != null) {
            throw new RuntimeException("用户名已存在");
        }
        if (email != null && userMapper.selectByEmail(email) != null) {
            throw new RuntimeException("邮箱已存在");
        }
        if (phone != null && userMapper.selectByPhone(phone) != null) {
            throw new RuntimeException("手机号已存在");
        }

        String passwordHash = passwordEncoder.encode(rawPassword);

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setEmail(email);
        user.setPhone(phone);
        user.setBalance(0.0);
        user.setRole("user");
        user.setStatus(1);
        userMapper.insert(user);
        user.setId(user.getId());

        return user;
    }

    public String login(String username, String rawPassword) {
        User user = userMapper.findActiveUserByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在或已禁用");
        }

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new RuntimeException("密码错误");
        }

        String token = JwtUtil.generateToken(
            user.getId(),
            user.getUsername(),
            user.getRole(),
            jwtSecret,
            jwtExpirationMs
        );

        return token;
    }

    public User validateToken(String token) {
        String jti = JwtUtil.getJti(token, jwtSecret);
        String redisKey = "auth:logout:" + jti;

        if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
            throw new RuntimeException("Token 已失效（已登出）");
        }

        Long userId = JwtUtil.parseToken(token, jwtSecret);
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) {
            throw new RuntimeException("用户不存在或已禁用");
        }
        return user;
    }

    public void logout(String token) {
        String jti = JwtUtil.getJti(token, jwtSecret);
        String redisKey = "auth:logout:" + jti;
        redisTemplate.opsForValue().set(redisKey, "1", Duration.ofMillis(jwtExpirationMs));
    }
}
