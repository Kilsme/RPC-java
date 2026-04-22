# RPC-java

一个基于 **Netty + Zookeeper** 的轻量级 Java RPC 框架练手项目，覆盖了从“服务发布”到“服务治理”的完整链路，适合作为学习 RPC 核心机制的参考实现。

> 定位：教学 / 实践导向，强调机制完整性与可读性，不是生产级 SDK。

---

## 1. 项目能力概览

### 通信与协议
- 基于 Netty 的长连接通信
- 自定义二进制协议（长度字段 + 魔数 + 消息类型 + 版本 + 序列化/压缩标识 + 消息体）
- 请求 / 响应 / 心跳请求 / 心跳响应四类消息
- 自定义编解码器：`KilsmeEncoder` / `KilsmeDecoder`

### 服务注册与发现
- 注册中心抽象接口：`ServiceRegistry`
- 默认门面：`DefaultServiceRegister`
- 已实现：Zookeeper（Curator ServiceDiscovery）
- 预留：Redis 注册中心
- Consumer 侧具备注册中心查询失败的本地缓存兜底

### 调用模型
- Consumer 通过 JDK 动态代理发起远程调用
- 支持接口调用与泛化调用（`GenericConsumer#$invoke`）
- Provider 基于接口方法反射执行

### 服务治理
- 负载均衡：`robin`（轮询）、`random`（随机）
- 限流：
  - Provider 侧全局并发限流（`ConcurrencyLimiter`）
  - Provider 侧连接维度速率限流（`RateLimiter`）
  - Consumer 侧在途请求与连接维度限流
- 重试策略（SPI）：`retrySame` / `failover` / `forking`
- 熔断：基于滑动窗口慢调用比例的断路器
- 降级：缓存回退 + Mock 回退组合策略

### 可扩展点（SPI / ServiceLoader）
- 序列化：JSON、Hessian
- 压缩：None、Gzip
- 重试策略：可插拔扩展

---

## 2. 端到端调用流程

1. Provider 启动，向本地 `ProviderRegistry` 注册“接口 -> 实例”映射。  
2. Provider 启动 Netty Server，并将服务元数据注册到注册中心。  
3. Consumer 创建接口代理，业务调用被拦截并组装为 `Request`。  
4. Consumer 从注册中心拉取实例列表，经过负载均衡选出目标 Provider。  
5. Consumer 发送异步请求，并在 in-flight 表中记录 `requestId -> Future`。  
6. Provider 收到请求后执行限流检查、服务定位、反射调用并返回 `Response`。  
7. Consumer 按 `requestId` 匹配响应并完成 Future，返回结果。  
8. 调用失败时按配置进入重试；重试仍失败则触发降级；节点异常时受熔断保护。  

---

## 3. 项目结构（关键目录）

```text
src/main/java/tech/insight/kilsme/rpc
├─ api/              # 示例服务接口与模型（Add/User）
├─ message/          # 协议消息体（Request/Response/Heartbeat）
├─ codec/            # 编解码器（KilsmeEncoder/KilsmeDecoder）
├─ provider/         # Provider 启动、服务注册表、请求处理
├─ consumser/        # Consumer 代理、连接管理、在途请求管理（目录名拼写即为 consumser）
├─ register/         # 注册中心抽象与实现选择器
├─ loadbalance/      # 负载均衡策略
├─ limit/            # 限流策略
├─ retry/            # 重试策略
├─ breaker/          # 熔断器
├─ fallback/         # 降级策略
├─ serialize/        # 序列化扩展点及实现
├─ compress/         # 压缩扩展点及实现
└─ handler/          # 心跳与流量统计等通用 Handler
```

---

## 4. 环境要求

- JDK 17（建议）
- Maven 3.8+
- Zookeeper（默认示例：`127.0.0.1:2181`）

> 当前 `pom.xml` 里 Java 版本配置存在差异（properties=8、compiler-plugin=16），实际运行时请以本机可用 JDK 为准。

---

## 5. 快速启动

### 5.1 启动 Zookeeper
确保本地可访问：

```text
127.0.0.1:2181
```

### 5.2 编译 / 测试

```bash
mvn test
```

### 5.3 启动 Provider

运行主类：

```text
tech.insight.kilsme.rpc.provider.ProviderApp
```

默认行为：
- 监听 `127.0.0.1:8889`
- 注册 `Add` 服务到 Zookeeper

### 5.4 启动 Consumer

运行主类：

```text
tech.insight.kilsme.rpc.consumser.ConsumerApp
```

默认会演示：
- 普通接口调用（`Add#add`）
- 泛化调用（`$invoke`）
- 对象参数透传与转换（`User` 合并）

---

## 6. 核心配置项（默认值）

### ConsumerProperties

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| workThreadNum | 4 | Consumer Netty 工作线程数 |
| connectTimeoutMs | 50000 | 建连超时（ms） |
| requestTimeoutMs | 50000 | 单次请求超时（ms） |
| methodTimeOutMs | 100000 | 方法总超时（含重试，ms） |
| loadBalancePolicy | robin | 负载均衡策略（robin/random） |
| retryPolicy | forking | 重试策略（retrySame/failover/forking） |
| serialize | json | 序列化算法 |
| compress | none | 压缩算法 |
| rpcPreSecond | 50 | Consumer 全局在途请求限流 |
| rpcPreChannel | 50 | 单连接限流 |
| slowRequestBreakRatio | 0.5 | 慢调用熔断比例阈值 |
| slowRequestMs | 1000 | 慢调用判定阈值（ms） |

### ProviderProperties

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| host | - | Provider 对外地址 |
| port | - | Provider 监听端口 |
| globalMaxRequest | 50 | Provider 全局并发上限 |
| preConsumerMaxRequest | 50 | 单连接速率上限 |
| workThreadNum | 4 | Provider worker 线程数 |
| serialize | json | 序列化算法 |
| compress | none | 压缩算法 |

### RegistryConfig

| 配置项 | 默认值 | 说明 |
|---|---|---|
| registerType | zookeeper | 注册中心类型（当前已实现 zookeeper） |
| connectString | - | 注册中心地址，例如 `127.0.0.1:2181` |

---

## 7. 协议说明（简版）

网络帧结构：

```text
| length(4) | magic(N) | type(1) | version(2) | ser+comp(1) | body(M) |
```

- `length`：后续载荷长度（不含 length 自身）
- `magic`：协议魔数（`Message.MAGIC`）
- `type`：消息类型（request/response/heartbeat）
- `version`：协议版本
- `ser+comp`：高 4 位序列化编码，低 4 位压缩编码
- `body`：序列化后的消息体（按需压缩）

---

## 8. SPI 扩展机制

项目使用 Java `ServiceLoader` 做插件发现。

### 内置扩展
- 序列化：
  - `tech.insight.kilsme.rpc.serialize.JsonSerializer`
  - `tech.insight.kilsme.rpc.serialize.HessianSerializer`
- 压缩：
  - `tech.insight.kilsme.rpc.compress.NoneCompression`
  - `tech.insight.kilsme.rpc.compress.GzipCompression`
- 重试：
  - `tech.insight.kilsme.rpc.retry.RetrySame`
  - `tech.insight.kilsme.rpc.retry.FailoverRetryPolicy`
  - `tech.insight.kilsme.rpc.retry.ForkingRetryPolicy`

---

## 9. 常见问题（FAQ）

### 1）Consumer 报“没有找到服务”
- Provider 未启动或未成功注册到 Zookeeper
- Consumer / Provider 注册中心地址不一致
- 服务名（接口全限定名）不一致

### 2）出现 requestId 不匹配或超时
- 请求已超时并从 in-flight 表移除，响应迟到
- 网络抖动导致发送失败或响应延迟
- `requestTimeoutMs` 过小

### 3）看起来限流没触发
- 请求并发不够高，未触发阈值
- 服务执行太快，难以形成并发堆积
- 建议提高压测并发、增加调用持续时长观察

---

## 10. 说明与建议

- 目录名 `consumser` 为当前项目既有命名，文档保持与代码一致。
- 该项目适合用于学习 RPC 基础架构、治理策略与 SPI 扩展设计。
- 若用于生产，请补充鉴权、观测、配置中心、单元/集成测试与安全加固。

---

## 11. License

详见仓库根目录 `LICENSE`。
