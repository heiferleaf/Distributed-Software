CREATE DATABASE IF NOT EXISTS spike_db;
USE spike_db;

CREATE TABLE IF NOT EXISTS `users` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '加密后的密码',
  `role` varchar(20) NOT NULL DEFAULT 'USER' COMMENT '用户角色：USER或ADMIN',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ShardingSphere 物理分表 0
CREATE TABLE IF NOT EXISTS `product_0` (
  `id` bigint(20) NOT NULL COMMENT '雪花算法主键',
  `name` varchar(100) NOT NULL COMMENT '商品名称',
  `description` varchar(255) DEFAULT NULL COMMENT '商品描述',
  `price` decimal(10,2) NOT NULL COMMENT '价格',
  `stock` int(11) NOT NULL DEFAULT 0 COMMENT '库存(预留，建议迁移至独立库存表)',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表_0';

-- ShardingSphere 物理分表 1
CREATE TABLE IF NOT EXISTS `product_1` (
  `id` bigint(20) NOT NULL COMMENT '雪花算法主键',
  `name` varchar(100) NOT NULL COMMENT '商品名称',
  `description` varchar(255) DEFAULT NULL COMMENT '商品描述',
  `price` decimal(10,2) NOT NULL COMMENT '价格',
  `stock` int(11) NOT NULL DEFAULT 0 COMMENT '库存(预留，建议迁移至独立库存表)',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表_1';

-- ==========================================
-- 新增：库存服务 (Inventory Service) 相关表
-- ==========================================
CREATE TABLE IF NOT EXISTS `inventory` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  `total_stock` int(11) NOT NULL DEFAULT 0 COMMENT '总库存',
  `available_stock` int(11) NOT NULL DEFAULT 0 COMMENT '可用库存',
  `locked_stock` int(11) NOT NULL DEFAULT 0 COMMENT '冻结库存(用于TCC软状态)',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存表';

-- ==========================================
-- 新增：订单服务 (Order Service) 相关表
-- 分库分表：通常按 user_id 或 order_id 分表，这里预留2张表作为示例
-- ==========================================
CREATE TABLE IF NOT EXISTS `orders_0` (
  `order_id` bigint(20) NOT NULL COMMENT '雪花算法生成的订单ID，也是分片键',
  `user_id` bigint(20) NOT NULL COMMENT '买家用户ID',
  `product_id` bigint(20) NOT NULL COMMENT '购买的商品ID',
  `amount` int(11) NOT NULL COMMENT '购买数量',
  `total_price` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '订单状态：0-初始化/待支付，1-已支付，2-已取消（超时/手动）',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`order_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表_0';

CREATE TABLE IF NOT EXISTS `orders_1` (
  `order_id` bigint(20) NOT NULL COMMENT '雪花算法生成的订单ID，也是分片键',
  `user_id` bigint(20) NOT NULL COMMENT '买家用户ID',
  `product_id` bigint(20) NOT NULL COMMENT '购买的商品ID',
  `amount` int(11) NOT NULL COMMENT '购买数量',
  `total_price` decimal(10,2) NOT NULL COMMENT '订单总金额',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '订单状态：0-初始化/待支付，1-已支付，2-已取消（超时/手动）',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`order_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表_1';

-- TCC 悬挂/幂等控制表 (用于解决 TCC 过程中的网络超时导致 Try/Confirm/Cancel 乱序执行问题)
CREATE TABLE IF NOT EXISTS `tcc_transaction_logs` (
    `tx_id` varchar(128) NOT NULL COMMENT '全局事务ID(XID)',
    `branch_id` varchar(128) NOT NULL COMMENT '分支事务ID',
    `action_type` varchar(20) NOT NULL COMMENT 'Try, Confirm, Cancel',
    `status` tinyint(2) NOT NULL COMMENT '0-执行中 1-成功 2-失败',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`tx_id`, `branch_id`, `action_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='TCC幂等防悬挂控制表';
