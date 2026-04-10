package com.whu.spikeuserservice.controller;

import com.whu.spikeuserservice.entity.UserEntity;
import com.whu.spikeuserservice.repository.UserRepository;
import com.whu.spikeuserservice.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redisTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostConstruct
    public void initAdmin() {
        if (userRepository.findByUsername("admin").isEmpty()) {
            UserEntity admin = new UserEntity();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole("ADMIN");
            userRepository.save(admin);
            System.out.println("Default admin created: admin / admin123");
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || password == null) return ResponseEntity.badRequest().body(Map.of("message", "Username and password required"));

        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Username already exists"));
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("ADMIN".equals(request.get("role")) ? "ADMIN" : "USER");
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        Optional<UserEntity> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            UserEntity user = userOpt.get();
            if (passwordEncoder.matches(password, user.getPassword())) {
                String accessToken = jwtUtil.generateAccessToken(username, user.getRole());
                String refreshToken = jwtUtil.generateRefreshToken(username, user.getRole());

                Map<String, Object> data = new HashMap<>();
                data.put("accessToken", accessToken);
                data.put("refreshToken", refreshToken);
                data.put("role", user.getRole());
                return ResponseEntity.ok(Map.of("message", "Login successful", "data", data));
            }
        }
        return ResponseEntity.status(401).body(Map.of("message", "Invalid credentials"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken == null || refreshToken.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Refresh token required"));
        }

        // 检查Redis黑名单
        if (Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + refreshToken))) {
            return ResponseEntity.status(401).body(Map.of("message", "Refresh token invalid/blacklisted"));
        }

        try {
            Claims claims = jwtUtil.parseToken(refreshToken);
            if (!"refresh".equals(claims.get("type"))) {
                return ResponseEntity.status(401).body(Map.of("message", "Not a refresh token"));
            }
            String username = claims.getSubject();
            String role = claims.get("role", String.class);

            String newAccessToken = jwtUtil.generateAccessToken(username, role);
            String newRefreshToken = jwtUtil.generateRefreshToken(username, role);

            // 可以选择将旧的也加入黑名单(类似单设备) - 这里简化就不加旧token黑名单了

            Map<String, Object> data = new HashMap<>();
            data.put("accessToken", newAccessToken);
            data.put("refreshToken", newRefreshToken);
            return ResponseEntity.ok(Map.of("message", "Token refreshed", "data", data));
        } catch (Exception e) {
            return ResponseEntity.status(401).body(Map.of("message", "Token expired or invalid"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refreshToken");
        if (refreshToken != null) {
            try {
                Claims claims = jwtUtil.parseToken(refreshToken);
                long expirationTime = claims.getExpiration().getTime();
                long ttl = expirationTime - System.currentTimeMillis();

                if (ttl > 0) {
                    redisTemplate.opsForValue().set("blacklist:" + refreshToken, "true", ttl, TimeUnit.MILLISECONDS);
                }
            } catch (Exception ignored) {
            }
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/info")
    public ResponseEntity<?> info(HttpServletRequest request) {
        return ResponseEntity.ok(Map.of("message", "Protected info accessed successfully", "mock_data", "Mock User Info data"));
    }
}
