# RPC-java

一个基于 **Netty + Zookeeper** 的轻量级 Java RPC 框架练手项目，覆盖了从"服务发布"到"服务治理"的完整链路，适合作为学习 RPC 核心机制的参考实现。

> 定位：教学 / 实践导向，强调机制完整性与可读性，不是生产级 SDK。

---

## 目录

1. [项目能力概览](#1-项目能力概览)
2. [端到端调用流程](#2-端到端调用流程)
3. [项目结构](#3-项目结构关键目录)
4. [设计模式](#4-设计模式)
5. [核心算法](#5-核心算法)
6. [兜底策略体系](#6-兜底策略体系)
7. [环境要求](#7-环境要求)
8. [快速启动](#8-快速启动)
9. [核心配置项](#9-核心配置项默认值)
10. [协议说明](#10-协议说明)
11. [SPI 扩展机制](#11-spi-扩展机制)
12. [常见问题](#12-常见问题faq)
13. [说明与建议](#13-说明与建议)
14. [License](#14-license)

---

## 1. 项目能力概览

### 通信与协议
- 基于 Netty 的长连接通信
- 自定义二进制协议（长度字段 + 魔数 + 消息类型 + 版本 + 序列化/压缩标识 + 消息体）
- 请求 / 响应 / 心跳请求 / 心跳响应四类消息
- 自定义编解码器：`KilsmeEncoder` / `KilsmeDecoder`

### 服务注册与发现
- 注册中心抽象接口：`ServiceRegistry`
- 默认门面：`DefaultServiceRegister`（工厂 + 门面组合）
- 已实现：Zookeeper（Curator ServiceDiscovery，临时节点，Provider 下线自动剔除）
- 预留：Redis 注册中心
- Consumer 侧具备注册中心查询失败时的本地缓存兜底

### 调用模型
- Consumer 通过 JDK 动态代理发起远程调用
- 支持强类型接口调用与**泛化调用**（`GenericConsumer#$invoke`）
- 泛化调用可传字符串参数类型 + Map 参数，由 Provider 反序列化为实际类型后反射执行
- Provider 基于接口方法签名进行反射调用，只暴露接口定义的公开方法

### 服务治理
- 负载均衡：`robin`（轮询）、`random`（随机）
- 限流：
  - Provider 侧全局并发限流（`ConcurrencyLimiter`）
  - Provider 侧连接维度速率限流（`RateLimiter`）
  - Consumer 侧全局并发限流 + 单 Provider 连接速率限流
- 重试策略（SPI）：`retrySame` / `failover` / `forking`
- 熔断：基于滑动窗口慢调用比例的三态断路器（CLOSED → OPEN → HALF_OPEN）
- 降级：缓存回退 → Mock 回退的组合链路

### 可扩展点（SPI / ServiceLoader）
- 序列化：JSON、Hessian（可自定义扩展）
- 压缩：None、Gzip（可自定义扩展）
- 重试策略：可通过 `@Spi` 注解 + SPI 文件插拔扩展

---

## 2. 端到端调用流程

```
Consumer                           Zookeeper/注册中心          Provider
   │                                      │                        │
   │ 1. createConsumerProxy(Add.class)    │                        │
   │────────────────────────────────────> │                        │
   │                                      │  2. registerService    │
   │                                      │ <─────────────────────│
   │ 3. add(1, 2) → Proxy.invoke()       │                        │
   │                                      │                        │
   │ 4. fetchServiceList(serviceName)     │                        │
   │────────────────────────────────────>│                        │
   │ <─────────── [provider1, ...]────── │                        │
   │                                                              │
   │ 5. loadBalance.select() → provider  │                        │
   │                                                              │
   │ 6. inFlightRequestTable(request)    │                        │
   │    ← 注册 requestId → Future        │                        │
   │                                                              │
   │ 7. channel.writeAndFlush(request) ─────────────────────────>│
   │                                                              │
   │                                          8. 限流校验         │
   │                                          9. 反射调用         │
   │                                          10. 返回 Response   │
   │ <────────────────────── Response ──────────────────────────  │
   │                                                              │
   │ 11. future.complete(response)                               │
   │     → 按 requestId 匹配                                     │
   │     → 调用方 get() 返回结果                                  │
   │                                                              │
   │  若失败 → 重试策略 → 失败 → 降级策略                         │
   │  节点异常 → 熔断保护                                         │
```

---

## 3. 项目结构（关键目录）

```text
src/main/java/tech/insight/kilsme/rpc
├─ api/              # 示例服务接口（Add）、降级实现（ConsumerAddImpl）与模型（User）
├─ message/          # 协议消息体（Request / Response / HeartbeatRequest / HeartbeatResponse）
├─ codec/            # 编解码器（KilsmeEncoder / KilsmeDecoder / RequestEncoder / ResponseEncoder）
├─ provider/         # Provider 启动（ProviderApp）、服务注册表、请求处理（ProviderServer）
├─ consumser/        # Consumer 代理（ConsumerProxyFactory）、连接管理（ConnectionManager）、
│                    # 在途请求管理（InFlightRequestManager）、泛化调用（GenericConsumer）
├─ register/         # 注册中心抽象（ServiceRegistry）、门面（DefaultServiceRegister）
│                    # ZK 实现（ZookeeperServiceRegister）、Redis 预留（RedisServiceRegister）
├─ loadbalance/      # 负载均衡策略接口与实现（RoundRobin / Random）
├─ limit/            # 限流器接口（Limiter）与实现（ConcurrencyLimiter / RateLimiter / BucketLimiter）
├─ retry/            # 重试策略接口（RetryPolicy）与实现（RetrySame / Failover / Forking）
├─ breaker/          # 熔断器接口（CircuitBreaker）与滑动窗口实现（ResponseTimeCircuitBreaker）
├─ fallback/         # 降级策略接口（Fallback）与实现（CacheFallback / MockFallback / DefaultFallback）
│                    # 降级注解（@RpcFallback）
├─ serialize/        # 序列化接口（Serializer）、管理器（SerializerManager）、JSON / Hessian 实现
├─ compress/         # 压缩接口（Compression）、管理器（CompressionManager）、None / Gzip 实现
├─ handler/          # 心跳检测（HeartbeatHandler）、流量统计（TrafficRecordHandler）
├─ metrices/         # RPC 调用指标（RpcCallMetrics）：方法、参数、耗时、成功/失败
├─ spi/              # SPI 注解（@Spi、@Extension）
├─ exception/        # 自定义异常（RpcException / LimitException）
└─ version/          # 协议版本枚举（Version）
```

---

## 4. 设计模式

项目覆盖了以下经典设计模式，每个都落在具体类上：

### 4.1 策略模式（Strategy）

框架中所有可替换的"行为"都抽成接口，运行时注入具体实现：

| 策略接口 | 具体实现 | 作用 |
|---|---|---|
| `LoadBalancer` | `RoundRobinLoadBalancer`、`RandomLoadBalancer` | 选择目标 Provider |
| `RetryPolicy` | `RetrySame`、`FailoverRetryPolicy`、`ForkingRetryPolicy` | 失败后重试行为 |
| `Serializer` | `JsonSerializer`、`HessianSerializer` | 请求/响应序列化 |
| `Compression` | `NoneCompression`、`GzipCompression` | 消息体压缩 |
| `Limiter` | `ConcurrencyLimiter`、`RateLimiter`、`BucketLimiter`（已弃用） | 流量控制 |
| `Fallback` | `CacheFallback`、`MockFallback`、`DefaultFallback` | 降级兜底 |
| `CircuitBreaker` | `ResponseTimeCircuitBreaker` | 熔断保护 |
| `ServiceRegistry` | `ZookeeperServiceRegister`、`RedisServiceRegister` | 注册中心 |

### 4.2 动态代理模式（Dynamic Proxy）

`ConsumerProxyFactory#createConsumerProxy(Class<I>)` 使用 **JDK 动态代理**：

```
Add addProxy = proxyFactory.createConsumerProxy(Add.class);
// addProxy.add(1, 2) → ConsumerInvocationHandler.invoke() → 远程 RPC 调用
```

调用方拿到的对象不是真实实现，而是一个拦截器（`ConsumerInvocationHandler`）。
它在 `invoke()` 中完成：服务发现 → 负载均衡 → 构建 Request → 异步发送 → 等待结果 → 重试/降级。

### 4.3 门面模式（Facade）

`DefaultServiceRegister` 是注册中心的统一门面：
- 对上层屏蔽 Zookeeper / Redis 等实现差异
- 内部持有真实的 `delegate` 实现，并维护一个本地缓存兜底

```
DefaultServiceRegister
  ├─ delegate: ZookeeperServiceRegister  ← 实际执行注册/发现
  └─ cache: Map<String, List<ServiceMetadata>>  ← 注册中心不可用时兜底
```

### 4.4 组合模式（Composite）

`DefaultFallback` 将 `CacheFallback` 和 `MockFallback` 组合为一个统一降级链路：

```
DefaultFallback.fallback()
  → 先尝试 CacheFallback（命中历史成功调用结果）
  → 命中则返回
  → 未命中则走 MockFallback（反射调用 @RpcFallback 注解指定的降级类）
```

### 4.5 责任链模式（Chain of Responsibility）

Netty Pipeline 本身即责任链。项目中 Provider 与 Consumer 的 Pipeline 各自组织了职责分明的处理链：

```
Provider Pipeline：
  TrafficRecordHandler（流量统计）
  → KilsmeDecoder（解码）
  → KilsmeEncoder（编码）
  → IdleStateHandler（空闲检测）
  → HeartbeatHandler（心跳）
  → LimitHandler（双向限流）
  → ProviderHandler（业务处理）

Consumer Pipeline：
  TrafficRecordHandler（流量统计）
  → KilsmeDecoder（解码）
  → KilsmeEncoder（编码）
  → IdleStateHandler（空闲检测）
  → HeartbeatHandler（心跳）
  → ConsumerHandler（响应处理）
```

### 4.6 模板方法模式（Template Method）

`KilsmeDecoder` 继承 Netty 的 `LengthFieldBasedFrameDecoder`：
- 父类处理粘包/半包（按长度字段切帧）
- 子类只需重写 `decode()` 实现业务解析逻辑（魔数校验 → 消息类型 → 反序列化）

### 4.7 工厂方法模式（Factory Method）

- `ConsumerProxyFactory`：`createConsumerProxy()`、`createLoadBalancer()`、`createRetryPolicy()` 根据配置创建具体策略实例。
- `DefaultServiceRegister#createServiceRegister()`：根据 `registerType` 选择 ZK 或 Redis 实现。

### 4.8 装饰器 / 双向处理器模式（Decorator / Duplex Handler）

`LimitHandler` 和 `TrafficRecordHandler` 都继承 `ChannelDuplexHandler`，同时拦截入站（读）和出站（写）：
- `LimitHandler`：入站时申请限流许可；出站响应写完后释放许可，形成完整"申请 → 处理 → 归还"生命周期。
- `TrafficRecordHandler`：对读写字节数分别计数，定时统计流量。

### 4.9 观察者 / 监听器模式（Observer / Listener）

- Netty `ChannelFuture.addListener()`：连接关闭后自动清理 Channel 缓存、释放 in-flight 限流计数。
- `CompletableFuture.whenComplete()`：请求完成（成功或失败）后自动触发 in-flight 表清理、超时任务取消、限流计数释放。

### 4.10 SPI（插件加载机制）

`SerializerManager`、`CompressionManager`、`RetryPolicyManager` 均通过 `ServiceLoader.load(SpiInterface.class)` 加载实现：
- 新增实现只需在 `META-INF/services/` 中注册，无需修改框架代码。
- 重试策略额外要求实现类标注 `@Spi(value = "策略名称")`，通过名称路由到具体策略。

---

## 5. 核心算法

### 5.1 指数退避（Exponential Backoff with Jitter）

**位置**：`RetrySame`

同机重试时，每次重试前按指数增长等待时间，并加随机扰动，避免惊群效应：

```java
// 第 retryCount 次重试等待时间（毫秒）
long nextDelay = 100L * (1L << retryCount) + random.nextInt(50);
// 单次退避上限：1000ms
if (nextDelay > 1000) nextDelay = 1000;
```

| 重试次数 | 基础等待 | 加 Jitter 后约 |
|:---:|---:|---:|
| 1 | 200ms | 200~249ms |
| 2 | 400ms | 400~449ms |
| 3 | 800ms | 800~849ms |

### 5.2 基于环形数组的滑动时间窗口（Sliding Window with Ring Buffer）

**位置**：`ResponseTimeCircuitBreaker`

使用固定大小的环形数组（10 个槽，每槽 1s），记录窗口内的请求总量与慢调用数量：

```
windowDurationMs = 10000ms，slotMs = 1000ms → 10 个 Slot
索引：0  1  2  3  4  5  6  7  8  9
             ↑currentIndex
```

- **时间推进**：每次 `recordRpc()` 时判断 `now - currentTime ≥ slotMs`，如超时则向前滑动 diff 个槽，并清零被覆盖的旧槽。
- **双重锁**：`ReentrantLock` + `volatile` 保证多线程下槽指针推进的安全。
- **熔断判断**：窗口内 `slowCount / totalCount > slowRatio` 且 `totalCount ≥ 5`（minRequest），触发熔断。

### 5.3 三态状态机（Three-State Circuit Breaker FSM）

**位置**：`ResponseTimeCircuitBreaker`

```
      慢调用比例 > 阈值
CLOSED ─────────────────────────> OPEN
  ↑                                 │
  │                      冷却期到期  │ (breakMs = 10s)
  │           (CAS OPEN→HALF_OPEN)  │
  │                                 ↓
  └──────── 探测成功 ────────── HALF_OPEN
             (CAS)                  │
                                    │ 探测失败
                                    └──────> OPEN（重置冷却）
```

状态转换全部通过 `AtomicReference<State>.compareAndSet()` 完成，保证并发下只有一个线程能完成转换。

### 5.4 CAS 无锁并发（Compare-and-Swap）

项目在多个关键路径上选择 CAS 替代锁：

| 位置 | 原子类型 | 用途 |
|---|---|---|
| `RateLimiter.nextTokens` | `AtomicLong` | 原子抢占下一个可用时间片 |
| `RoundRobinLoadBalancer.index` | `AtomicInteger` | 轮询计数器无锁递增 |
| `Request.REQUEST_COUNTER` | `AtomicInteger` | 全局 requestId 无锁生成 |
| `ResponseTimeCircuitBreaker.state` | `AtomicReference<State>` | 熔断状态无锁转换 |
| `ResponseTimeCircuitBreaker.slots[i]` | `AtomicInteger` | 槽内请求计数无锁累加 |
| `TrafficRecordHandler.upload/download` | `AtomicLong` | 流量字节数无锁统计 |
| `ProviderServer.LimitHandler.GLOBAL_PERMITS` | `AtomicInteger` | Channel 级在途请求计数 |

### 5.5 时间轮（Hashed Wheel Timer）

**位置**：`InFlightRequestManager`

请求发出后，使用 Netty 的 `HashedWheelTimer`（100ms 精度，256 槽）注册超时任务：

```java
timeoutTimer = new HashedWheelTimer(100, TimeUnit.MILLISECONDS, 256);

Timeout timeout = timeoutTimer.newTimeout(
    t -> responseFuture.completeExceptionally(new TimeoutException()),
    requestTimeoutMs,
    TimeUnit.MILLISECONDS
);

// 请求完成后取消超时任务，避免误触发
responseFuture.whenComplete((f, e) -> timeout.cancel());
```

相比 `ScheduledThreadPoolExecutor`，时间轮批量管理定时任务的开销更小，适合大量短期超时场景。

### 5.6 时间片速率限流（Token-per-interval Rate Limiting）

**位置**：`RateLimiter`

不使用令牌补充定时任务，而是通过"抢占下一个可用时间片"来实现平滑速率控制：

```
每秒 N 个请求 → intervalNs = 1s / N（纳秒）

请求到来时：
  pre = nextTokens.get()              // 上一个请求占用的时间片终点
  if now + MAX_QUEUE_NS < pre → 拒绝  // 排队等待会超过上限 (500ms)
  CAS: nextTokens = now + intervalNs  // 抢占当前时间片终点
```

与令牌桶（`BucketLimiter`，已弃用）的对比：

| 特性 | `RateLimiter`（当前） | `BucketLimiter`（已弃用） |
|---|---|---|
| 平滑度 | 纳秒级平滑 | 每秒重置，突刺 |
| 定时任务 | 无需定时线程 | 需要 EventLoop 定时补充 |
| 突发容忍 | 有限（500ms 队列窗口） | 无（每秒全部放行） |

### 5.7 基于信号量的并发限流

**位置**：`ConcurrencyLimiter`

使用 `java.util.concurrent.Semaphore` 控制"同时在处理中的请求数"上限：

```java
Semaphore semaphore = new Semaphore(limitNum);
semaphore.tryAcquire()  // 申请许可，失败立即返回 false（非阻塞）
semaphore.release()     // 请求处理完成后归还
```

与速率限流的区别：并发限流限的是"同时存在的请求数"，速率限流限的是"单位时间通过的请求数"。

### 5.8 轮询负载均衡（Round Robin）

**位置**：`RoundRobinLoadBalancer`

```java
AtomicInteger index = new AtomicInteger();
int i = index.getAndIncrement() % serviceList.size();
return serviceList.get(Math.abs(i)); // abs 处理溢出负数
```

### 5.9 随机负载均衡（Random Selection）

**位置**：`RandomLoadBalancer`

使用 `ThreadLocalRandom.current().nextInt(size)` 在候选列表中随机选取节点。

---

## 6. 兜底策略体系

项目在三个层次设计了兜底机制：

### 6.1 注册中心层兜底

**位置**：`DefaultServiceRegister`

注册中心查询成功时刷新本地缓存；查询失败时直接返回缓存，不让上层感知：

```
fetchServiceList(service)
  → delegate.fetchServiceList()  ← 成功：刷新 cache，返回结果
  → 失败（ZK 异常）              ← 降级：返回 cache.getOrDefault(service, [])
```

### 6.2 连接层兜底

**位置**：`ConnectionManager`

- `computeIfAbsent`：同一地址只建一条连接（复用）。
- 连接关闭时自动清理缓存（避免下次用到失效 Channel）。
- `getChannel()` 返回前检查 `channel.isActive()`，失效则移除并让上层触发重试。

### 6.3 超时兜底（时间轮）

**位置**：`InFlightRequestManager`

每个 in-flight 请求注册一个时间轮任务，超时后强制让 Future 异常结束（`TimeoutException`），避免业务线程永久阻塞。请求正常完成后取消超时任务，两条路径互斥完成。

### 6.4 降级策略链

**位置**：`DefaultFallback` → `CacheFallback` → `MockFallback`

```
触发降级
  │
  ├─ CacheFallback（优先级高）
  │    key = Method + args[]（方法 + 入参完全匹配）
  │    value = 最近一次成功调用的返回值
  │    注意：返回 null 也缓存（用 NULL_OBJECT 占位区分"未缓存"）
  │
  └─ MockFallback（兜底）
       读接口上的 @RpcFallback(MockImpl.class) 注解
       反射创建 MockImpl 实例（要求无参构造器）
       用相同的方法名 + 入参调用 MockImpl，返回 Mock 结果
```

**使用示例**：

```java
// 服务接口标注降级实现类
@RpcFallback(ConsumerAddImpl.class)
public interface Add {
    Integer add(int a, int b);
}

// 降级实现类（需实现同一接口，提供无参构造器）
public class ConsumerAddImpl implements Add {
    @Override
    public Integer add(int a, int b) {
        return -1;  // Mock 返回值
    }
}
```

`@RpcFallback` 注解使用 `RetentionPolicy.RUNTIME`，在运行期通过反射读取，无需修改框架代码即可为接口绑定不同的降级实现。

### 6.5 熔断保护

**位置**：`CircuitBreakerManager` + `ResponseTimeCircuitBreaker`

每个 Provider 实例对应一个独立的熔断器（`CircuitBreakerManager` 负责按 `ServiceMetadata` 创建或复用）。

调用前检查 `breaker.allowRequest()`：OPEN 状态下直接返回熔断 Future；HALF_OPEN 只放行探测请求。调用结束后 `breaker.recordRpc(metrics)` 记录本次调用是否为慢请求，触发状态机转换。

---

## 7. 环境要求

- JDK 17（建议）
- Maven 3.8+
- Zookeeper（默认示例：`127.0.0.1:2181`）

> 当前 `pom.xml` 里 Java 版本配置存在差异（properties=8、compiler-plugin=16），实际运行时请以本机可用 JDK 为准。

---

## 8. 快速启动

### 8.1 启动 Zookeeper

确保本地可访问：

```text
127.0.0.1:2181
```

### 8.2 编译 / 测试

```bash
mvn test
```

### 8.3 启动 Provider

运行主类：

```text
tech.insight.kilsme.rpc.provider.ProviderApp
```

默认行为：
- 监听 `127.0.0.1:8889`
- 注册 `Add` 服务到 Zookeeper（接口名即为服务名）

### 8.4 启动 Consumer

运行主类：

```text
tech.insight.kilsme.rpc.consumser.ConsumerApp
```

默认会演示：
- 普通接口调用（`Add#add`）
- 泛化调用（`GenericConsumer#$invoke`，传字符串类型 + Map/基本类型参数）
- 对象参数透传与转换（`User.mergeAge(User, User)`，Consumer 传 HashMap，Provider 自动转 User 对象）

---

## 9. 核心配置项（默认值）

### ConsumerProperties

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| workThreadNum | 4 | Consumer Netty 工作线程数 |
| connectTimeoutMs | 50000 | 建连超时（ms） |
| requestTimeoutMs | 50000 | 单次请求超时（ms） |
| methodTimeOutMs | 100000 | 方法总超时含重试（ms） |
| loadBalancePolicy | robin | 负载均衡策略（robin/random） |
| retryPolicy | forking | 重试策略（retrySame/failover/forking） |
| serialize | json | 序列化算法 |
| compress | none | 压缩算法 |
| rpcPreSecond | 50 | Consumer 全局并发在途请求上限 |
| rpcPreChannel | 50 | 单 Provider 连接速率限流（req/s） |
| slowRequestBreakRatio | 0.5 | 慢调用比例熔断阈值 |
| slowRequestMs | 1000 | 慢调用判定时间（ms） |

### ProviderProperties

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| host | - | Provider 对外地址 |
| port | - | Provider 监听端口（示例：8889） |
| globalMaxRequest | 50 | Provider 全局并发上限（ConcurrencyLimiter） |
| preConsumerMaxRequest | 50 | 单连接速率上限（RateLimiter） |
| workThreadNum | 4 | Provider worker 线程数 |
| serialize | json | 序列化算法 |
| compress | none | 压缩算法 |

### RegistryConfig

| 配置项 | 默认值 | 说明 |
|---|---|---|
| registerType | zookeeper | 注册中心类型（当前已实现 zookeeper） |
| connectString | - | 注册中心地址，例如 `127.0.0.1:2181` |

---

## 10. 协议说明

### 帧结构

```text
┌──────────┬─────────────┬──────────┬───────────┬────────────────┬──────────┐
│ length   │  magic      │ type(1B) │version(2B)│ ser+comp(1B)   │ body     │
│  (4B)    │  (N bytes)  │          │           │ [7:4]=ser      │ (M bytes)│
│          │             │          │           │ [3:0]=compress │          │
└──────────┴─────────────┴──────────┴───────────┴────────────────┴──────────┘
```

- `length`：后续载荷总长度（不含 length 自身 4 字节）
- `magic`：协议魔数（`Message.MAGIC`，用于识别是否为本 RPC 协议帧）
- `type`：消息类型（1=request / 2=response / 3=heartbeat_req / 4=heartbeat_resp）
- `version`：协议版本（当前 v1=0）
- `ser+comp`：高 4 位序列化编码，低 4 位压缩编码（支持 16 种序列化 × 16 种压缩组合）
- `body`：序列化（+可选压缩）后的消息体；若 body 长度 < 256 字节则不压缩

### 消息类型

| 类型码 | Java 类 | 方向 |
|:---:|---|---|
| 1 | `Request` | Consumer → Provider |
| 2 | `Response` | Provider → Consumer |
| 3 | `HeartbeatRequest` | 任意方 → 对端 |
| 4 | `HeartbeatResponse` | 对端回复 |

### 心跳机制

- Provider 侧：`IdleStateHandler(readerIdle=30s, writerIdle=5s)`
  - 写空闲 5s → 发送 `HeartbeatRequest`
  - 读空闲 30s → 关闭连接（对端已失联）
- Consumer 侧：同样的 IdleStateHandler + HeartbeatHandler 组合
- 心跳响应回传 `requestTime` 便于计算 RTT

---

## 11. SPI 扩展机制

项目使用 Java `ServiceLoader` 做插件发现，配合 `@Spi` 注解实现按名路由。

### 如何新增序列化算法

1. 实现 `Serializer` 接口，提供 `code()`（< 16）、`getName()`、`serialize()`、`deserialize()`。
2. 在 `META-INF/services/tech.insight.kilsme.rpc.serialize.Serializer` 中追加全限定类名。
3. 在 `ConsumerProperties.serialize` / `ProviderProperties.serialize` 中填写新实现的名称。

### 如何新增重试策略

1. 实现 `RetryPolicy` 接口，标注 `@Spi("自定义策略名")`。
2. 在 `META-INF/services/tech.insight.kilsme.rpc.retry.RetryPolicy` 中追加全限定类名。
3. 在 `ConsumerProperties.retryPolicy` 中填写策略名。

### 内置扩展列表

| 类型 | 名称 / 键值 | 实现类 |
|---|---|---|
| 序列化 | `json` | `JsonSerializer` |
| 序列化 | `hessian` | `HessianSerializer` |
| 压缩 | `none` | `NoneCompression` |
| 压缩 | `gzip` | `GzipCompression` |
| 重试 | `retrySame` | `RetrySame` |
| 重试 | `failover` | `FailoverRetryPolicy` |
| 重试 | `forking` | `ForkingRetryPolicy` |

---

## 12. 常见问题（FAQ）

### 1）Consumer 报"没有找到服务"
- Provider 未启动或未成功注册到 Zookeeper
- Consumer / Provider 注册中心地址不一致
- 服务名（接口全限定名）不一致

### 2）出现 requestId 不匹配或超时
- 请求已超时并从 in-flight 表移除，响应迟到
- 网络抖动导致发送失败或响应延迟
- `requestTimeoutMs` 过小，可适当调大

### 3）看起来限流没触发
- 请求并发不够高，未触发阈值
- 服务执行太快，难以形成并发堆积
- 建议提高压测并发、增加调用持续时长观察

### 4）Mock 降级不生效
- 确认接口上标注了 `@RpcFallback(MockImpl.class)`
- 确认 `MockImpl` 实现了该接口且有**无参构造器**
- 确认 `MockImpl` 的对应方法有合理返回值（非抛异常）

### 5）熔断状态一直是 OPEN
- 窗口期内慢调用比例持续超阈值，冷却期（breakMs=10s）过后触发 HALF_OPEN 探测
- 可降低 `slowRequestBreakRatio` 或增大 `slowRequestMs` 来缓解

---

## 13. 说明与建议

- 目录名 `consumser` 为当前项目既有命名，文档保持与代码一致。
- 该项目适合用于学习 RPC 基础架构、设计模式、算法选型与 SPI 扩展设计。
- 若用于生产，请补充鉴权、分布式追踪（TraceId）、配置中心、单元/集成测试与安全加固。

---

## 14. License

详见仓库根目录 `LICENSE`。
