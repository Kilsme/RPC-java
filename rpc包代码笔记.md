# `rpc` 包代码笔记（中文详细版）

> 说明：这份笔记基于 `src/main/java/tech/insight/kilsme/rpc` 目录下的全部源码整理，**不修改源码**，只用于帮助理解整体设计、调用流程、限流与重试机制。

---

## 1. 这个 RPC Demo 的目标是什么

这个项目的目标很简单：

- **Provider（生产者）**：把本地实现好的服务接口暴露出去，并把自己注册到注册中心（这里是 Zookeeper）。
- **Consumer（消费者）**：从注册中心拿到可用 Provider 地址，然后通过 Netty 发送请求，远程调用 Provider 上的方法。
- **中间层**：
  - 消息协议：`Request` / `Response` / `Message`
  - 编解码：`RequestEncoder` / `ResponseEncoder` / `KilsmeDecoder`
  - 注册中心：`ServiceRegistry` / `DefaultServiceRegister` / `ZookeeperServiceRegister`
  - 限流：Consumer 和 Provider 都有自己的限流逻辑
  - 重试：失败后按策略重试

你可以把整个系统理解成：

1. Provider 启动并注册服务；
2. Consumer 向注册中心查找服务；
3. Consumer 选择一个 Provider；
4. Consumer 构造 `Request` 并发出去；
5. Provider 收到请求，反射调用本地实现；
6. Provider 回写 `Response`；
7. Consumer 收到响应，唤醒等待线程并返回结果。

---

## 2. 目录里的主要文件分别干什么

### 2.1 入口类

- `Main.java`
  - 目前只是一个空入口，更多是占位。
- `ConsumerApp.java`
  - 消费者启动示例，演示如何发起 RPC 调用。
- `ProviderApp.java`
  - 提供者启动示例，演示如何注册服务并启动 Netty 服务端。
- `ZKdemo.java`
  - 单独演示 Curator + Zookeeper 的最小注册示例。

### 2.2 API 层

- `api/Add.java`
  - 定义 RPC 服务接口，也就是“契约”。
  - Consumer 通过这个接口调用，Provider 通过这个接口实现。

### 2.3 Provider 侧

- `provider/AddImpl.java`
  - `Add` 接口的实现类。
- `provider/ProviderApp.java`
  - Provider 启动入口。
- `provider/ProviderServer.java`
  - Provider 的核心服务端逻辑。
- `provider/ProviderRegistry.java`
  - 本地服务注册表，保存“接口 -> 实现对象”。
- `provider/ProviderProperties.java`
  - Provider 配置对象。

### 2.4 Consumer 侧

- `consumser/ConsumerApp.java`
  - Consumer 启动入口。
- `consumser/ConsumerProxyFactory.java`
  - Consumer 的动态代理工厂，是整个消费端最核心的类。
- `consumser/ConnectionManager.java`
  - 管理到 Provider 的连接复用。
- `consumser/InFlightRequestManager.java`
  - 管理在途请求、Future、超时和限流。
- `consumser/ConsumerProperties.java`
  - Consumer 配置对象。

### 2.5 注册中心

- `register/ServiceRegistry.java`
  - 注册中心统一接口。
- `register/DefaultServiceRegister.java`
  - 注册中心门面，内部按配置选择 Zookeeper 或 Redis。
- `register/ZookeeperServiceRegister.java`
  - Zookeeper 注册中心实现。
- `register/RedisServiceRegister.java`
  - Redis 注册中心占位实现。
- `register/RegistryConfig.java`
  - 注册中心配置。
- `register/ServiceMetadata.java`
  - 服务实例元数据，记录 host / port / serviceName。

### 2.6 消息与协议

- `message/Message.java`
  - 协议基础定义，包含魔数、消息类型等。
- `message/Request.java`
  - RPC 请求对象。
- `message/Response.java`
  - RPC 响应对象。

### 2.7 编解码

- `codec/KilsmeDecoder.java`
  - 把字节流反序列化成 `Request` 或 `Response`。
- `codec/RequestEncoder.java`
  - 把 `Request` 编码成字节流。
- `codec/ResponseEncoder.java`
  - 把 `Response` 编码成字节流。

### 2.8 限流

- `limit/Limiter.java`
  - 限流器抽象接口。
- `limit/ConcurrencyLimiter.java`
  - 基于 `Semaphore` 的并发限流。
- `limit/RateLimiter.java`
  - 基于时间间隔的速率限流。
- `limit/BucketLimiter.java`
  - 旧版本令牌桶限流，已弃用。

### 2.9 负载均衡

- `loadbalance/LoadBalancer.java`
  - 负载均衡接口。
- `loadbalance/RandomLoadBalancer.java`
  - 随机选择 Provider。
- `loadbalance/RoundRobinLoadBalancer.java`
  - 轮询选择 Provider。

### 2.10 重试

- `retry/RetryPolicy.java`
  - 重试策略接口。
- `retry/RetryContext.java`
  - 重试上下文。
- `retry/RetrySame.java`
  - 同节点重试。
- `retry/FailoverRetryPolicy.java`
  - 故障转移重试。
- `retry/ForkingRetryPolicy.java`
  - 并发竞速重试。

### 2.11 异常

- `exception/RpcException.java`
  - RPC 通用异常。
- `exception/LimitException.java`
  - 限流异常。

---

## 3. Provider 侧大致流程

### 3.1 启动入口：`ProviderApp`

Provider 启动时做三件事：

1. 创建注册中心配置 `RegistryConfig`；
2. 创建 `ProviderProperties`，设置 host、port；
3. 创建 `ProviderServer`，注册服务实现 `Add.class -> new AddImpl()`；
4. 调用 `providerServer.start()`。

### 3.2 `ProviderServer` 里发生了什么

`ProviderServer` 是 Provider 的核心。

它做的事情可以拆成三部分：

#### A. 初始化本地服务注册表

- `registry` 是 `ProviderRegistry`，只负责保存本地“接口 -> 实现对象”的映射。
- `register(Add.class, new AddImpl())` 只是把实现先放到本地内存里。
- **注意**：这一步还没有注册到 Zookeeper。

#### B. 启动 Netty 服务端

- 使用 `ServerBootstrap`。
- `bossEventLoopGroup` 负责接收连接。
- `workerEventLoopGroup` 负责处理连接上的 IO 事件。
- 服务端用的是 `childHandler(...)`，因为服务端会接收到很多“子连接”。

#### C. 把本地服务注册到注册中心

- `registry.allServiceName()` 会取出所有已注册的接口名。
- `buildMetadata(serviceName)` 会把 `serviceName + host + port` 组装成 `ServiceMetadata`。
- 然后交给 `serviceRegister.registerService(...)`。
- 如果配置的是 Zookeeper，就会走 `ZookeeperServiceRegister`。

### 3.3 Provider 收到请求后如何处理

Provider 的 pipeline 大致是：

`KilsmeDecoder -> ResponseEncoder -> LimitHandler -> ProviderHandler`

#### `LimitHandler`

这个处理器负责 Provider 侧限流：

- 先尝试全局并发限流 `globallLimiter.tryAcquire()`；
- 再尝试当前连接的速率限流 `channelLimiter.tryAcquire()`；
- 如果限流失败，就直接返回 `Response.fail("provider限流", requestId)`；
- 如果放行，就继续往下传给 `ProviderHandler`。

#### `ProviderHandler`

这个处理器负责真正执行业务：

1. 根据 `request.getServiceName()` 找到本地实现包装器；
2. 根据 `methodName + paramsClass` 反射找到目标方法；
3. 在实现对象上执行方法；
4. 把结果封装成 `Response.success(...)`；
5. 写回给客户端。

---

## 4. Consumer 侧大致流程

### 4.1 启动入口：`ConsumerApp`

`ConsumerApp` 的流程是：

1. 配置注册中心信息；
2. 配置消费者限流、线程、超时等参数；
3. 创建 `ConsumerProxyFactory`；
4. 通过 `proxyFactory.createConsumerProxy(Add.class)` 创建动态代理；
5. 线程并发调用 `add(1, 2)`。

### 4.2 为什么 Consumer 要用动态代理

因为 Consumer 想写成这种形式：

```java
Add add = proxyFactory.createConsumerProxy(Add.class);
add.add(1, 2);
```

看起来像本地方法调用，但实际上底层会变成 RPC 请求。

这就是动态代理的作用：

- 你调用接口方法；
- 代理拦截这个调用；
- 它帮你做服务发现、负载均衡、网络发送、等待响应；
- 最后返回真正结果。

### 4.3 `ConsumerProxyFactory` 的核心职责

这个类是 Consumer 侧最核心的地方。

它做了四件事：

1. 初始化注册中心客户端；
2. 创建负载均衡器；
3. 创建重试策略；
4. 创建动态代理对象。

### 4.4 `invoke()` 的完整流程

每次你调用 `add.add(1, 2)`，实际都会进入 `ConsumerInvocationHandler.invoke(...)`。

流程如下：

#### 第一步：处理 `Object` 基础方法

如果调用的是 `toString()`、`hashCode()`、`equals()`，就直接本地处理，不走 RPC。

#### 第二步：从注册中心获取服务列表

- 调用 `registry.fetchServiceList(interfaceClass.getName())`；
- 如果没有服务，直接抛 `RpcException("没有找到服务")`。

#### 第三步：负载均衡选一个 Provider

- 从 `serviceMetadataList` 中选择一个目标 Provider；
- 具体策略由 `LoadBalancer` 决定，比如随机或者轮询。

#### 第四步：构建 `Request`

`buildRequest()` 会把以下信息塞进去：

- `serviceName`：接口全限定名
- `methodName`：方法名
- `paramsClass`：参数类型数组
- `params`：参数值数组
- `requestId`：请求 ID

#### 第五步：发起异步 RPC

`callRpcAsync()` 会：

1. 把 `requestId -> CompletableFuture<Response>` 放进 `InFlightRequestManager`；
2. 通过 `ConnectionManager` 获取到 Provider 的 Channel；
3. `writeAndFlush(request)` 发消息出去；
4. 返回这个 Future。

#### 第六步：同步等待异步结果

虽然发送是异步的，但 Consumer 这边最终会用：

```java
requestFuture.get(timeout, TimeUnit.MILLISECONDS)
```

来阻塞等待结果。

#### 第七步：如果失败就进入重试

如果发送超时、连接失败、服务端异常等，就进入 `doRetry(...)`：

- 根据重试策略执行；
- 可能同节点重试、故障转移、或者并发竞速；
- 最后拿到一个 `Response` 或抛异常。

#### 第八步：处理响应

- `code == 200`，返回 `res`；
- 否则抛出 `RpcException(errorMessage)`。

---

## 5. 为什么 Provider 里是 `childHandler`，Consumer 里是 `handler`

这是 Netty 的概念差异：

### Consumer 使用 `Bootstrap.handler(...)`

Consumer 是客户端：

- 一般主动连到一个或多个 Provider；
- `Bootstrap` 代表客户端启动器；
- `handler(...)` 是给客户端连接设置 pipeline。

### Provider 使用 `ServerBootstrap.childHandler(...)`

Provider 是服务端：

- 服务端会接受很多客户端连接；
- `ServerBootstrap` 负责监听端口；
- 每来一个新连接，就要给这个“子连接”初始化 pipeline；
- 所以用 `childHandler(...)`。

简单记：

- **客户端**：一个连接，常用 `handler`
- **服务端**：很多子连接，常用 `childHandler`

---

## 6. 注册中心流程：Provider 怎么注册，Consumer 怎么发现

### 6.1 Provider 侧：先本地注册，再发布到 Zookeeper

你可以把 Provider 侧理解成两层：

#### 第一层：本地注册表 `ProviderRegistry`

- 保存接口和实现对象的对应关系；
- 比如 `Add.class -> AddImpl`。
- 这是给 Provider 自己反射调用用的。

#### 第二层：注册中心 `ServiceRegistry`

- 启动时，把服务元数据发布到 Zookeeper；
- 让 Consumer 能发现这个服务。

### 6.2 Consumer 侧：从注册中心拉取服务列表

Consumer 不直接拿 ProviderRegistry，因为 `ProviderRegistry` 是 Provider 本地内存结构，Consumer 根本访问不到。

Consumer 能做的是：

1. 去注册中心查 `serviceName`；
2. 得到可用 Provider 列表；
3. 选一个目标地址；
4. 发请求。

### 6.3 为什么 Consumer 用 `DefaultServiceRegister`

因为它是注册中心门面，内部统一封装：

- 具体是 Zookeeper 还是 Redis；
- 调用方都不用关心。

当前代码里实际使用的是 Zookeeper。

---

## 7. `ProviderRegistry` 里的 `invocation` 是什么

`ProviderRegistry` 里有一个内部类：`invocation<I>`。

它不是“调用动作”本身，而是一个**服务实例包装器**，里面保存：

- 接口类型 `interfaceClass`
- 实现对象 `serviceInstance`

它的作用是：

1. 用接口名去定位服务；
2. 用方法名和参数类型找接口方法；
3. 最终在实现对象上执行这个方法。

### 为什么不直接拿实现类反射？

因为 RPC 框架更希望暴露的是**接口契约**，而不是具体实现类：

- 接口是对外协议；
- 实现类是内部细节；
- 这样更稳、更清晰。

### 你之前问过的一个点

> 为什么查的是 `ProviderRegistry`，又要去 Zookeeper？

答案是：

- `ProviderRegistry` 是 Provider 的**本地注册表**；
- Zookeeper 是给 Consumer 看的**服务发现中心**。

两者角色不同，不是同一个东西。

---

## 8. 消息协议与请求响应如何对应

### 8.1 `Message`

`Message` 定义了统一协议头：

- `MAGIC`：魔数，用来识别是不是自己的协议
- `messageType`：请求还是响应
- `body`：JSON 内容

### 8.2 `Request`

请求包含：

- `requestId`
- `serviceName`
- `methodName`
- `paramsClass`
- `params`

这个设计的意思是：

> Provider 收到请求后，只靠这些信息就能定位到具体方法并反射调用。

### 8.3 `Response`

响应包含：

- `res`：结果
- `code`：状态码
- `errorMessage`：错误信息
- `requestId`：对应请求 ID

`requestId` 很重要，因为 Consumer 端是并发发送多个请求的，必须靠它把响应和请求一一匹配起来。

---

## 9. 编解码流程

### 9.1 请求发送时：`RequestEncoder`

把 `Request` 编码成：

`length + magic + type + body(JSON)`

其中：

- `length` 不是整个报文长度，而是不包含前面 4 字节长度字段本身；
- `body` 是 JSON 字节数组。

### 9.2 Provider / Consumer 接收时：`KilsmeDecoder`

解码器会：

1. 先按长度字段切帧，解决粘包/半包；
2. 校验魔数；
3. 读消息类型；
4. 读取 body；
5. 按消息类型反序列化成 `Request` 或 `Response`。

### 9.3 响应返回时：`ResponseEncoder`

Provider 返回 `Response` 时，也会按同样协议编码：

`length + magic + type + body(JSON)`

这样 Consumer 才能正确解出来。

---

## 10. Consumer 和 Provider 的限流流程

这部分是你最关心的重点之一。

---

### 10.1 Consumer 侧限流：`InFlightRequestManager`

Consumer 侧的目标不是保护 Provider，而是：

- 控制自己同时发出去多少请求；
- 控制每个 Provider 的在途请求量；
- 避免请求太多导致本地堆积或把对端打爆。

#### Consumer 侧有两层限流

##### 1）全局限流：`globalLimiter`

- 类型是 `ConcurrencyLimiter`；
- 表示 Consumer 当前最多允许多少个请求同时在途；
- `tryAcquire()` 失败，就直接拒绝。

##### 2）单 Provider 限流：`channelLimiterMap`

- 每个 `ServiceMetadata` 对应一个 `Limiter`；
- 当前实现用的是 `RateLimiter`；
- 表示某个 Provider 每秒最多能接受多少请求。

#### Consumer 限流具体流程

在 `inFlightRequestTable(...)` 里：

1. 先创建一个 `CompletableFuture<Response>`；
2. 申请全局许可；
3. 再申请该 Provider 的许可；
4. 都成功后，把 `requestId -> future` 放进表；
5. 再注册超时任务；
6. 最后返回 future。

#### 为什么要存 Future，不直接存 Response

因为请求刚发出去时，**Response 还没回来**。

所以必须先保存一个“将来某一刻会完成”的对象：

- 这个对象就是 `CompletableFuture<Response>`；
- 等 Provider 返回后，再通过 `requestId` 找到它并 `complete(response)`；
- 调用方这边 `get()` 就会被唤醒。

#### `completeRuest()` 为什么要 `remove()`

因为：

- 响应只需要处理一次；
- 处理完以后就应该把这条在途记录删掉；
- 否则会内存泄漏，并且可能重复完成。

所以它的意义是：

> 先从表里取出并删除对应 Future，再用 response 完成它。

这不是“remove 后返回 response”，而是：

- `remove(requestId)` 取到对应的 Future；
- 然后 `future.complete(response)`。

---

### 10.2 Provider 侧限流：`ProviderServer.LimitHandler`

Provider 侧的限流是为了保护自己不被打爆。

#### Provider 侧也有两层

##### 1）全局限流：`globallLimiter`

- 限制整个 Provider 实例能同时处理多少请求；
- 一旦超了，就直接返回失败响应。

##### 2）单连接限流：`channelLimiter`

- 每个客户端连接都会绑定一个 `RateLimiter`；
- 用于限制单个 Consumer 对这个 Provider 的打压。

#### Provider 侧限流流程

在 `channelRead(...)` 里：

1. 先尝试全局并发许可；
2. 再尝试这个 channel 的速率许可；
3. 如果失败，直接返回 `Response.fail("provider限流", requestId)`；
4. 如果成功，继续 `ctx.fireChannelRead(request)` 交给业务处理器。

#### 什么时候释放许可

在 `write(...)` 和 `channelInactive(...)` 里会归还。

这意味着：

- 请求进来时占用许可；
- 响应写出去后释放许可；
- 连接断开时也会释放残留许可。

---

### 10.3 为什么你会看到“同时发 10 个请求，但还是有 5 个成功”

这通常是因为 Consumer 和 Provider 两端的限流不是同一个开关，可能出现这种情况：

- Consumer 只控制“发请求的节奏”和“在途数”；
- Provider 才真正决定“接不接这个请求”；
- 如果 Consumer 端限流配置较宽松，10 个请求可能会一起发出去；
- Provider 端限流如果只限制到一部分请求，就会出现“有的成功，有的被拒绝”。

另外还要注意：

- 你以为“只有一个 Provider”，但 Consumer 的 10 个线程是并发的；
- 真正进入 Provider 的顺序和时间点，不一定完全一致；
- 只要限流器的窗口允许部分请求先通过，就会有成功请求。

---

## 11. 为什么用 `CompletableFuture`，而不是直接存 `Response`

因为 RPC 是异步到达的：

- 请求发出去时，结果还没回来；
- 如果你直接存 `Response`，那一开始只能存空值；
- `CompletableFuture` 的意义是：
  - 先把“结果占位符”放进去；
  - 响应回来后补全；
  - 等待中的线程自动恢复。

这就是异步转同步的核心手段。

---

## 12. 重试策略流程

Consumer 在调用失败后，会进入重试逻辑。

### 12.1 `RetryContext`

它把一次重试需要的所有信息打包：

- 失败的 Provider
- 可用 Provider 列表
- 方法总超时预算
- 单次请求超时
- 负载均衡器
- 真正执行 RPC 的函数

### 12.2 `RetrySame`

含义：在同一个失败节点上重试几次。

流程：

1. 计算指数退避等待时间；
2. 不能超过方法总超时；
3. sleep 一会儿；
4. 再对同一个 Provider 发起 RPC；
5. 失败就继续，直到达到最大重试次数。

### 12.3 `FailoverRetryPolicy`

含义：失败后换一个 Provider。

流程：

1. 复制 Provider 列表；
2. 去掉失败节点；
3. 重新通过负载均衡挑一个新的 Provider；
4. 发起 RPC；
5. 等待响应。

### 12.4 `ForkingRetryPolicy`

含义：并发请求多个 Provider，谁先返回用谁。

流程：

1. 对所有 Provider 都发一次请求；
2. `CompletableFuture.anyOf(...)` 等待最先完成的那个；
3. 用最先完成的结果返回。

---

## 13. 负载均衡流程

Consumer 先从注册中心拿到一组 Provider，然后通过负载均衡器挑一个。

### 13.1 `RandomLoadBalancer`

- 随机选一个实例；
- 简单直接。

### 13.2 `RoundRobinLoadBalancer`

- 按顺序轮流选；
- 用原子计数器保证并发安全。

### 13.3 为什么需要负载均衡

因为注册中心返回的往往不是一个节点，而是多个节点。

如果不做负载均衡，就会出现：

- 所有请求都打到同一个实例；
- 热点过热；
- 其他实例闲置。

---

## 14. `childHandler` 和 `handler` 的一句话总结

- **Consumer**：`Bootstrap.handler(...)`
  - 配置客户端连接的 pipeline。
- **Provider**：`ServerBootstrap.childHandler(...)`
  - 配置每个新连接的 pipeline。

这是 Netty 服务端/客户端模型的正常区别，不是写错了。

---

## 15. 这份代码里几个值得你笔记记录的点

### 15.1 本地注册和注册中心不是一回事

- `ProviderRegistry`：本地保存“接口 -> 实现对象”；
- `ZookeeperServiceRegister`：对外发布“服务地址”；
- 一个给 Provider 本身用，一个给 Consumer 用。

### 15.2 `requestId` 是请求和响应配对的关键

Consumer 同时发多个请求时，必须靠 `requestId` 匹配回包。

### 15.3 `CompletableFuture` 是“占位符”

不是结果本身，而是“将来结果到达”的容器。

### 15.4 限流是双端的

- Consumer：防止本地在途请求过多；
- Provider：防止自己被过载。

### 15.5 重试要受总超时约束

不能无限重试，否则就会把一个调用拖死。

---

## 16. 代码里几个容易混淆的地方

### 16.1 `serviceName` 是什么

- 在 `Request` 里，`serviceName` 一般就是接口全限定名；
- 在 Zookeeper 里，也会用这个名字作为服务节点名称。

### 16.2 为什么 Consumer 不能直接拿 Provider 实例

因为 Consumer 和 Provider 是两个进程、两个 JVM、两套内存空间。

Consumer 只能拿到：

- 服务地址
- 服务列表
- 连接信息

拿不到 Provider 的本地对象。

### 16.3 为什么 Provider 需要反射

因为 RPC 传过来的是：

- 方法名
- 参数类型
- 参数值

Provider 必须根据这些信息动态找到对应方法再执行。

---

## 17. 一条完整链路的文字版流程图

### Provider 启动时

1. `ProviderApp.main()`
2. 创建 `ProviderProperties`
3. 创建 `ProviderServer`
4. `providerServer.register(Add.class, new AddImpl())`
5. `providerServer.start()`
6. `ProviderRegistry` 保存本地实现
7. `ServerBootstrap` 启动监听
8. 把 `ServiceMetadata` 注册到 Zookeeper

### Consumer 调用时

1. `ConsumerApp.main()`
2. 创建 `ConsumerProxyFactory`
3. 创建 `Add` 动态代理
4. 调用 `add.add(1, 2)`
5. `invoke()` 被触发
6. 去注册中心拿 Provider 列表
7. 负载均衡选一个 Provider
8. 组装 `Request`
9. `InFlightRequestManager` 创建 Future 并限流
10. `ConnectionManager` 获取 Channel
11. `RequestEncoder` 编码并发送
12. Provider 收到后 `KilsmeDecoder` 解码
13. `LimitHandler` 限流
14. `ProviderHandler` 反射调用 `AddImpl.add`
15. `ResponseEncoder` 编码响应
16. Consumer 的 `ConsumerHandler` 收到响应
17. `completeRuest(requestId, response)` 完成 Future
18. `invoke()` 返回结果

---

## 18. 适合你做笔记的总结版

你可以直接记成下面这几句：

### Provider 侧

- 先把接口实现注册到本地 `ProviderRegistry`；
- 启动 Netty 服务端；
- 把服务元数据注册到 Zookeeper；
- 收到请求后，先限流，再反射执行方法，再返回响应。

### Consumer 侧

- 从注册中心查找可用 Provider；
- 用负载均衡选一个节点；
- 构造 `Request`；
- 将请求放到 `CompletableFuture` 中等待结果；
- 发送到 Provider；
- 收到响应后按 `requestId` 唤醒等待线程；
- 如失败则按重试策略补救。

### 限流

- Consumer 限流：保护自己和下游；
- Provider 限流：保护服务端资源；
- 两边都有限流，所以你看到的成功/失败数可能不完全一致。

---

## 19. 一些源码阅读时要注意的点

这部分不是改代码，只是阅读笔记里值得标出来的“注意事项”：

1. `ConnectionManager.getChannel()` 里有连接缓存逻辑，阅读时要注意缓存 key 的写法。
2. `RetrySame` 使用了指数退避和随机抖动。
3. `ForkingRetryPolicy` 不是“重试同一个节点”，而是“并发请求多个节点”。
4. `BucketLimiter` 已弃用，当前更应该看 `RateLimiter`。
5. `DefaultServiceRegister` 有本地缓存，注册中心查不到时会回退缓存。
6. `KilsmeDecoder` 先判断魔数，再判断消息类型，再反序列化 body。

---

## 20. 结尾一句话

这套 RPC 的核心就是：

> **Consumer 负责发现、选择、发送和等待；Provider 负责注册、限流、反射执行和返回；中间用注册中心、Netty 协议、Future 和重试策略把两边串起来。**

如果你要继续做笔记，建议按下面四个小标题继续整理：

- Consumer 侧流程
- Provider 侧流程
- 注册中心与 Zookeeper
- 限流、重试、负载均衡

