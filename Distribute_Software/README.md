# 📑 [定稿] CNEC 电商系统：业务需求与分布式技术映射

## 1. 用户领域 (User Domain)
*   **基础业务**：用户注册、加密登录
*   **分布式挑战**：
    *   服务无法保证每一次被定位到相同的实例，导致会话信息不能被后端识别。
*   **解决技术方案**：
    *   **鉴权**：**Gateway + JWT**。采用无状态鉴权，Token 携带用户信息，避开复杂的 Session 同步。
    *   **安全**：**BCrypt** 强哈希加密 + 双 JWT 机制 + RT黑名单，保证密码安全，用户令牌安全。

## 2. 商品领域 (Product Domain)
*   **基础业务**：商品列表展示、详情页查询
*   **分布式挑战**：
    *   **高并发读压**：商品详情是全站流量最大的地方，直接冲击数据库会导致宕机。
    *   **数据实时性**：分布式环境下，后台修改价格后，如何快速同步到所有缓存节点。
    *   **海量数据存储**：单表数据量过大导致查询与写入性能衰退。
*   **解决技术方案**：
    1.  **多级缓存 (Multi-Level Cache) 与防穿透**：
        *   **L1 (JVM 进程内缓存)**：使用 **Caffeine**。存放极其热门的商品（如大促 Banner、Top 10 商品）。直接从内存读取，零网络开销。
        *   **L2 (分布式缓存)**：**Redis**。存放海量常规商品数据。处理缓存的雪崩（随机过期时间）、穿透（**布隆过滤器 Bloom Filter**+空值缓存）、击穿（分布式锁）。
    2.  **缓存和库数据一致性 (Consistency)**：
        *   **基于 Binlog 的异步同步**。利用 **Canal** 监听 MySQL Binlog 变更，发送消息到 MQ，由消费者统一清理缓存，实现最终一致性。
    3.  **计算与存储分流 (Sharding)** *(补充)*：
        *   **分库分表 (ShardingSphere)**：基于 Hash 取模算法对 `product` 表进行水平分表，全局唯一主键交由 **Snowflake (雪花算法)** 生成。游标分页解决深分页瓶颈。

## 3. 库存领域 (Stock Domain)
*   **基础业务**：库存扣减、库存预热、库存锁定（下单未支付时）、库存释放（取消订单时）。
*   **分布式挑战**：
    *   **超卖问题 (Overselling)**：多个实例同时扣减同一商品的库存，容易出现数据由于并发冲突导致的负数。
    *   **写冲突**：数据库行级锁在极高并发下的等待耗时。
*   **解决技术方案**：
    *   **架构防线** *(补充)*：**Redis Lua 原子扣减 + Redisson 分布式锁 + 数据库悲观锁 (`FOR UPDATE`)**。三管齐下，既保证了内存的高速拦截，又保证了落地数据库时的数据绝对安全与排他性。
    *   **Seata TCC 模式**：引入冻结库存软状态，实现库存的最终一致性，消除数据库长时间写锁开销和压力。
    *   **核心逻辑**：管理员发布商品时主动**缓存预热**。
    *   **事务参与**：作为 **Seata 的从属资源 (RM)**，严格建立自定义事务日志表 (`tcc_transaction_logs`) 用于精确识别 Try/Confirm/Cancel 阶段，有效防止**悬挂**（Cancel早于Try）、**空回滚**和**重复提交（幂等校验）**。

## 4. 订单领域 (Order Domain)
*   **基础业务**：创建订单、订单状态机流转（待支付->已支付->取消/超时）、订单查询。
*   **分布式挑战**：
    *   **分布式事务协同**：下单动作跨越了 User（扣钱）、Stock（扣货）和 Order（生成单），如何保证原子性。
    *   **高并发写对数据库的压力**：订单的创建需要写多张表，瞬间峰值极易压垮 DB。
    *   **雪崩效应**：当下游库存服务变慢时，如何防止订单服务被拖死。
*   **解决技术方案**：
    *   **前后端异步轮询解耦** *(补充)*：前端提交购买请求后，立即获取生成的 Snowflake 订单 ID，依靠 JS 定时器进行状态轮询，获得极佳交互体验。
    *   **异步削峰**：将海量请求打包为 `order.create` 消息发送至 **RabbitMQ**，由消费者平滑抓取执行，保护下游。
    *   **一致性核心**：**Seata TCC 模式**。消费者充当全局事务发起者（TM），协调跨微服务事务。
    *   **数据路由** *(补充)*：同样利用 **ShardingSphere** 对 `orders` 表进行水平拆分（分片键为订单ID）。
    *   **可用性核心**：**Sentinel 熔断降级**。如果库存服务响应慢，立即切断，防止线程池耗尽。

## 5. 支付领域 (Payment Domain)
*   **基础业务**：订单超时未支付的自动解冻库存、支付时的结果反馈。
*   **分布式挑战**：
    *   **响应速度要求**：对于支付结果的响应速度。
*   **解决技术方案**：
    *   **异步解耦**：通过 **RabbitMQ** 实现削峰填谷；利用 **死信队列 / 延迟队列** 处理超时未支付单。另外通过订单状态机 (`status` 字段配合乐观匹配)，防止死信队列和支付服务同时发起的并行冲突。

---

## 6. 基础设施与部署逻辑 (Infrastructure & Deployment)

| 目标特性                   | 关键技术实现                                                         |
| :--------------------- | :------------------------------------------------------------- |
| **高性能 (Performance)**  | **Redis 缓存** + **Feign 连接池优化** + **微服务轻量化**。                   |
| **高并发 (Concurrency)**  | **Nacos 负载均衡**（多线程伸缩）+ **Sentinel  + MQ 流量治理**（削峰填谷） + **Lua 预扣减**。 |
| **高可靠 (Reliability)**  | **Seata 分布式事务 TCC 补偿机制** + **MySQL Sharding 持久化存储**。                      |
| **高可用 (Availability)** | **Docker-Compose 容器健康检查**（挂了自动重启）+ **Sentinel 熔断**（弃车保帅）。      |
| **一致性 (Consistency)**  | **Canal 旁路缓存清理** + **Seata 全局协调器隔离机制**。                                 |

---

### 系统架构流转图 (Mermaid)

```mermaid
graph TD
    %% 客户端与网关层
    Client[Web/H5 Client<br/>(轮询/双Token)] -->|HTTP Request| Gateway(Spring Cloud Gateway<br/>JWT/限流)
    
    %% 微服务层
    subgraph Microservices [Spring Cloud Alibaba Microservices]
        UserSvc[User Service<br/>鉴权/身份]
        ProductSvc[Product Service<br/>商品/布隆/多级缓存]
        OrderSvc[Order Service<br/>雪花算法/异步消费者]
        InventorySvc[Inventory Service<br/>TCC/排他锁]
    end
    
    Gateway -->|路由/透传头| UserSvc
    Gateway -->|路由/透传头| ProductSvc
    Gateway -->|路由/透传头| OrderSvc
    
    %% 核心业务流转
    OrderSvc -->|1.发送消息削峰| MQ_Exch_Order((RabbitMQ<br/>order.exchange))
    MQ_Exch_Order -->|2.平滑消费| OrderConsumer[Order Message Consumer<br/>Seata TM 主控]
    OrderConsumer -.->|3.开启全局事务| SeataTC((Seata TC<br/>8091))
    OrderConsumer -->|4A. Feign RPC| InventorySvc
    OrderConsumer -->|4B. 本地写库| OrderDB
    
    %% 缓存与同步流转
    ProductSvc <-->|查询/回写/防击穿| RedisCache[(Redis<br/>分布式缓存/Lua)]
    InventorySvc <-->|预热/扣增量| RedisCache
    
    Canal[Canal Server] -->|监听Binlog| ProductDB
    Canal -->|变更消息| MQ_Exch_Canal((RabbitMQ<br/>canal.exchange))
    MQ_Exch_Canal -.->|驱除缓存| ProductSvc
    
    %% 数据库层
    subgraph Databases [MySQL Sharding Sphere Cluster]
        UserDB[(user_db)]
        ProductDB[(product_0, product_1)]
        OrderDB[(orders_0, orders_1<br/>tcc_logs)]
        InvDB[(inventory<br/>tcc_logs)]
    end
    
    UserSvc --> UserDB
    ProductSvc --> ProductDB
    InventorySvc --> InvDB
    
    %% TCC 分支协同
    InventorySvc -.->|Try/Confirm/Cancel| SeataTC
    OrderSvc -.->|Try/Confirm/Cancel| SeataTC

    %% 注册中心
    Nacos((Nacos Server<br/>配置/注册中心)) -.->|服务发现与配置| Microservices
```
