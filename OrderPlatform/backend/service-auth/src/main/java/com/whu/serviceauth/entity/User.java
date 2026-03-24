package com.whu.serviceauth.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class User implements Serializable {
    private Long id;
    private String username;
    private String passwordHash;
    private String email;
    private String phone;
    private Double balance;
    private String role;  // "user" 或 "admin"
    private Integer status;  // 1启用，0禁用
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
