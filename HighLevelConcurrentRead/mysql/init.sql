CREATE DATABASE IF NOT EXISTS shop DEFAULT CHARACTER SET utf8mb4;

USE shop;

CREATE TABLE IF NOT EXISTS product (
                                       id          BIGINT PRIMARY KEY AUTO_INCREMENT,
                                       name        VARCHAR(100) NOT NULL,
    description TEXT,
    price       DECIMAL(10,2) NOT NULL,
    stock       INT NOT NULL DEFAULT 0,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP
    );

INSERT INTO product (name, description, price, stock) VALUES
                                                          ('iPhone 15', '苹果手机旗舰款', 7999.00, 100),
                                                          ('MacBook Pro', '苹果笔记本电脑', 14999.00, 50),
                                                          ('AirPods Pro', '主动降噪耳机', 1899.00, 200),
                                                          ('iPad Air', '平板电脑', 4799.00, 80),
                                                          ('Apple Watch', '智能手表', 3299.00, 120);