# RPC-java

一个基于 **Netty + Zookeeper** 的轻量级 Java RPC Demo，用于学习和演示 RPC 框架的核心机制：

- 服务注册与发现
- 自定义协议编解码
- 动态代理调用
- 负载均衡
- 限流 / 超时 / 重试 / 熔断 / 降级

> 说明：这是教学/实践导向项目，重点在架构链路与治理能力，不是生产级 SDK。

---

## 1. 项目特性

- **通信层**：基于 Netty，使用 Pipeline 组织编解码、心跳、限流和业务处理。
- **注册中心**：默认使用 Zookeeper（Curator）；Redis 注册中心预留扩展接口。
- **协议层**：自定义请求/响应模型（`Request`、`Response`）与编解码器（`KilsmeEncoder`、`KilsmeDecoder`）。
- **调用层**：Consumer 侧通过 JDK 动态代理将本地接口调用转为 RPC 请求。
- **服务治理**：
  - 负载均衡：随机、轮询
  - 限流：并发限流、速率限流（Consumer/Provider 双侧）
  - 重试：同机重试、故障转移、并发竞速
  - 熔断：基于调用结果与耗时进行断路保护
  - 降级：缓存回退、Mock 回退
- **可扩展性**：序列化（JSON/Hessian）、压缩（None/Gzip）、重试策略均可插拔。

---

## 2. 核心流程（端到端）

1. Provider 启动，注册本地服务实现到 `ProviderRegistry`。
2. Provider 启动 Netty Server，并将服务元数据注册到 Zookeeper。
3. Consumer 创建接口代理，发起方法调用时构建 `Request`。
4. Consumer 从注册中心查询服务实例列表，执行负载均衡选择目标 Provider。
5. 通过 Netty Channel 异步发送请求，同时在 `InFlightRequestManager` 登记 `requestId -> Future`。
6. Provider 收到请求后完成限流校验、服务定位、反射调用，返回 `Response`。
7. Consumer 收到响应后按 `requestId` 完成对应 Future，返回业务结果。
8. 若失败则进入重试；重试仍失败则降级；严重异常时熔断保护。

---

## 3. 目录结构（关键模块）

```text
src/main/java/tech/insight/kilsme/rpc
├─ api/              # 示例接口与数据模型
├─ codec/            # 协议编解码
├─ message/          # Request/Response/心跳消息
├─ provider/         # 服务端启动、服务注册表、请求处理
├─ consumser/        # 客户端代理、连接管理、在途请求管理（目录名为 consumser）
├─ register/         # 注册中心抽象与实现（ZK/Redis占位）
├─ loadbalance/      # 负载均衡策略
├─ retry/            # 重试策略
├─ breaker/          # 熔断器
├─ fallback/         # 降级策略
├─ limit/            # 限流策略
├─ serialize/        # 序列化扩展
├─ compress/         # 压缩扩展
└─ handler/          # 通用 Netty Handler（心跳、流量记录等）
```

---

## 4. 环境要求

- JDK 17（项目运行日志显示使用 JDK17）
- Maven 3.8+
- Zookeeper（本地默认示例地址 `127.0.0.1:2181`）

> `pom.xml` 中编译参数存在 Java 版本配置差异（properties 里是 8，compiler-plugin 里是 16），建议统一为你本机实际使用版本（如 17）。

---

## 5. 快速开始

### 5.1 启动 Zookeeper

请先确保本地 Zookeeper 已启动并可连接 `127.0.0.1:2181`。

### 5.2 构建项目

```bash
mvn clean package -DskipTests
```

### 5.3 启动 Provider

运行主类：`tech.insight.kilsme.rpc.provider.ProviderApp`

### 5.4 启动 Consumer

运行主类：`tech.insight.kilsme.rpc.consumser.ConsumerApp`

启动后可在日志中看到：
- Provider 端服务注册日志
- Consumer 端请求发送与响应回包日志

---

## 6. 配置建议

- **注册中心地址**：确保 Consumer 与 Provider 使用同一套注册中心配置。
- **请求超时**：建议根据业务耗时调大 `requestTimeoutMs` / `methodTimeOutMs`。
- **限流阈值**：根据压测结果调整全局限流与单连接限流参数。
- **日志级别**：排错阶段可开 `DEBUG`，日常建议 `INFO`，避免日志噪音过大。

---

## 7. 常见问题（FAQ）

### Q1：Consumer 报错“没有找到服务”
通常是 Provider 未成功注册到 Zookeeper，或两端配置的 `serviceName`/注册中心路径不一致。

### Q2：为什么会出现“未找到对应请求 requestId”
一般是请求已超时并从 In-Flight 表移除，响应才迟到到达；或请求发送失败后 Future 已异常完成。

### Q3：为什么并发限流值看起来没生效
可能请求没有形成足够并发重叠（服务执行太快），建议增加 Provider 处理耗时或提高压测并发持续度再观察。

---

## 8. 后续可演进方向

- 增加统一配置中心与热更新
- 增加请求追踪（traceId）和链路监控指标导出
- 增加鉴权、签名与传输加密
- 增加灰度发布、权重路由与多机房容灾
- 增加更完整的自动化测试与压测基线

---

## 9. License

本项目遵循仓库内 `LICENSE` 文件约定。

