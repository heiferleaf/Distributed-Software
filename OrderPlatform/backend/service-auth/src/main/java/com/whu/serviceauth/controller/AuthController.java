package com.whu.serviceauth.controller;

import com.whu.serviceauth.entity.User;
import com.whu.serviceauth.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestParam @NotBlank String username,
            @RequestParam @NotBlank String password,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone) {

        User user = authService.register(username, password, email, phone);
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "注册成功");
        resp.put("data", user);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestParam @NotBlank String username,
            @RequestParam @NotBlank String password) {

        String token = authService.login(username, password);
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "登录成功");
        Map<String, String> data = new HashMap<>();
        data.put("token", token);
        data.put("tokenType", "Bearer");
        resp.put("data", data);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "无效的 Token"));
        }
        String token = authHeader.substring(7);
        authService.logout(token);
        return ResponseEntity.ok(Map.of("code", 200, "message", "登出成功"));
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("code", 401, "message", "无效的 Token"));
        }
        String token = authHeader.substring(7);
        User user = authService.validateToken(token);
        Map<String, Object> resp = new HashMap<>();
        resp.put("code", 200);
        resp.put("message", "Token 有效");
        resp.put("data", Map.of("userId", user.getId(), "username", user.getUsername(), "role", user.getRole()));
        return ResponseEntity.ok(resp);
    }
}
