package tech.insight.kilsme.rpc.consumser;

import io.netty.channel.Channel;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import tech.insight.kilsme.rpc.breaker.CircuitBreaker;
import tech.insight.kilsme.rpc.breaker.CircuitBreakerManager;
import tech.insight.kilsme.rpc.exception.RpcException;
import tech.insight.kilsme.rpc.fallback.CacheFallback;
import tech.insight.kilsme.rpc.fallback.DefaultFallback;
import tech.insight.kilsme.rpc.fallback.Fallback;
import tech.insight.kilsme.rpc.fallback.MockFallback;
import tech.insight.kilsme.rpc.loadbalance.LoadBalancer;
import tech.insight.kilsme.rpc.loadbalance.RandomLoadBalancer;
import tech.insight.kilsme.rpc.loadbalance.RoundRobinLoadBalancer;
import tech.insight.kilsme.rpc.message.Request;
import tech.insight.kilsme.rpc.message.Response;
import tech.insight.kilsme.rpc.metrices.RpcCallMetrics;
import tech.insight.kilsme.rpc.register.DefaultServiceRegister;
import tech.insight.kilsme.rpc.register.ServiceMetadata;
import tech.insight.kilsme.rpc.register.ServiceRegistry;
import tech.insight.kilsme.rpc.retry.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Consumer 动态代理工厂。
 * <p>
 * 核心职责：
 * 1) 拦截接口方法调用并构造 Request；
 * 2) 从注册中心发现服务并负载均衡选址；
 * 3) 通过 ConnectionManager 发送请求；
 * 4) 等待响应并执行超时/重试/熔断逻辑。
 */
@Slf4j
public class ConsumerProxyFactory {

    // 连接管理器：复用到同一 Provider 地址的连接。
    private final ConnectionManager manager;
    private final ServiceRegistry registry;
    private final ConsumerProperties consumerProperties;
    private final InFlightRequestManager inFlightRequestManager;
    private final CircuitBreakerManager circuitBreakerManager;
    private Fallback fallback;
    private final RetryPolicyManager retryPolicyManager;

    /**
     * 初始化消费端基础组件。
     *
     * <p>这里会完成三件关键事情：
     * <ul>
     *     <li>初始化在途请求管理器：负责 requestId 和 Future 的映射、超时和限流；</li>
     *     <li>初始化注册中心门面：对外屏蔽 Zookeeper / Redis 等实现差异；</li>
     *     <li>初始化连接管理器：负责和 Provider 建链、复用连接、收响应。</li>
     * </ul>
     *
     * @param consumerProperties 消费端配置（超时、重试、负载均衡、注册中心等）
     */
    public ConsumerProxyFactory(ConsumerProperties consumerProperties) throws Exception {
        this.inFlightRequestManager = new InFlightRequestManager(
                consumerProperties);
        this.circuitBreakerManager = new CircuitBreakerManager(consumerProperties);
        // 使用统一门面，屏蔽具体注册中心实现差异。
        this.registry = new DefaultServiceRegister();
        this.registry.init(consumerProperties.getRegistryConfig());
        this.manager = new ConnectionManager(inFlightRequestManager, consumerProperties);
        this.consumerProperties = consumerProperties;
        this.fallback = new DefaultFallback(new CacheFallback(), new MockFallback());
        this.retryPolicyManager = new RetryPolicyManager();
    }

    /**
     * 为目标接口创建消费端代理。
     *
     * <p>调用这个代理对象上的方法时，会进入 {@link ConsumerInvocationHandler#invoke}，
     * 然后走完整的 RPC 发送/接收流程。
     */
    @SuppressWarnings("unchecked")
    public <I> I createConsumerProxy(Class<I> interfaceClass) {
        // 通过 JDK 动态代理把本地接口调用转为远程 RPC 请求。
        // 调用方拿到的并不是一个真实实现类，而是一个“拦截器代理对象”。
        return (I) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class[]{interfaceClass}, new ConsumerInvocationHandler(interfaceClass,
                        createLoadBalancer(),
                        createRetryPolicy(consumerProperties.getRetryPolicy())));
    }

    /**
     * 根据配置创建重试策略实例。
     */
    private RetryPolicy createRetryPolicy(String name) {
        RetryPolicy retryPolicy = retryPolicyManager.getRetryPolicy(name);
        if (retryPolicy == null) {
            throw new IllegalArgumentException("没有或者重试策略" + name);
        }
        return retryPolicy;
    }

    /**
     * 根据配置创建负载均衡策略实例。
     */
    private LoadBalancer createLoadBalancer() {
        switch (this.consumerProperties.getLoadBalancePolicy()) {
            case "robin":
                return new RoundRobinLoadBalancer();
            case "random":
                return new RandomLoadBalancer();
            default:
                throw new IllegalArgumentException(this.consumerProperties.getLoadBalancePolicy() + "不支持");
        }
    }

    /**
     * 代理调用处理器：每次接口方法调用都会进入这里。
     */
    public class ConsumerInvocationHandler implements InvocationHandler {
        final Class<?> interfaceClass;
        final LoadBalancer loadBalancer;
        final RetryPolicy retryPolicy;

        public ConsumerInvocationHandler(Class<?> interfaceClass, LoadBalancer loadBalancer, RetryPolicy retryPolicy) {
            this.interfaceClass = interfaceClass;
            this.loadBalancer = loadBalancer;
            this.retryPolicy = retryPolicy;
        }

        /**
         * 执行一次完整的 RPC 调用。
         *
         * <p>这段逻辑是 Consumer 侧的“主入口”，每次业务代码调用代理对象的方法都会进入这里。
         * 整体可以理解为：
         * <ol>
         *     <li>先判断是不是 `toString/equals/hashCode` 这类基础方法；</li>
         *     <li>从注册中心拿到服务列表；</li>
         *     <li>通过负载均衡选一个目标 Provider；</li>
         *     <li>把方法名、参数、服务名打包成 Request；</li>
         *     <li>通过 Netty 异步发送，并等待 Future 完成；</li>
         *     <li>如果失败，则交给重试策略处理；</li>
         *     <li>最后把 Response 转成真正的业务返回值。</li>
         * </ol>
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // 先处理 Object 通用方法，避免走远程调用。
            if (method.getDeclaringClass() == Object.class) {
                if (method.getName().equals("toString")) {
                    return "YY Proxy Consumer" + interfaceClass.getName();
                }
                if (method.getName().equals("hashCode")) {
                    return System.identityHashCode(proxy);
                }
                if (method.getName().equals("equals")) {
                    return proxy == args[0];
                }
                throw new UnsupportedOperationException("代理对象不支持这个函数");
            }
            boolean genericInvoke = method.getName().equals("$invoke");
            String serviceName = genericInvoke ? args[0].toString() : interfaceClass.getName();
            // 下面这些注释是对“注册中心角色”的理解提示：
            // consumer -> center -> provider，consumer 不直接访问 provider 本地对象，
            // 只能通过注册中心发现服务地址；而注册中心通常还会承担通知、临时节点、心跳等职责。
            // 1) 从注册中心查可用 Provider 列表。
            List<ServiceMetadata> serviceMetadata = new ArrayList<>(registry.fetchServiceList(serviceName));//进行包装保证可以进行操作
            // 2) 选择一个 Provider 并复用/创建连接。
            // 使用负载均衡策略从多个实例中挑一个目标节点。
            ServiceMetadata provider = decideProvider(serviceMetadata);
            Request request = buildRequest(method, args);
            // 同步等待异步结果返回。
            // 这里先发起异步调用，再通过 Future.get() 阻塞等待结果，形成“异步发送、同步拿结果”的效果。
            RpcCallMetrics rpcCallMetrics = RpcCallMetrics.createRpcCallMetrics(method, args, provider);
            CircuitBreaker breaker = circuitBreakerManager.createOrGetBreaker(provider);
            if (provider == null) {
                //进行降级处理
                return fallback.fallback(rpcCallMetrics);
            }
            try {
                CompletableFuture<Response> requestFuture = callRpcAsync(request, provider);
                Response response = requestFuture.get(consumerProperties.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
                rpcCallMetrics.complete(response);
                breaker.recordRpc(rpcCallMetrics);
                fallback.recordMetrics(rpcCallMetrics);
                return processResponse(response);
            } catch (Exception e) {
                //进行重试
                rpcCallMetrics.errorComplete(e);
                breaker.recordRpc(rpcCallMetrics);
                //如果重试还是抛出异常，进行降级
            }
            try {
                return processResponse(doRetry(rpcCallMetrics, serviceMetadata));
            } catch (Exception e) {
                //进行降级
                return fallback.fallback(rpcCallMetrics);

            }


        }

        private ServiceMetadata decideProvider(List<ServiceMetadata> candidate) {
            while (!candidate.isEmpty()) {
                ServiceMetadata select = this.loadBalancer.select(candidate);
                CircuitBreaker breaker = circuitBreakerManager.createOrGetBreaker(select);
                if (breaker.allowRequest()) {
                    return select;
                } else {
                    candidate.remove(select);
                }
            }
            return null;//进行判断返回值是不是null来进行判断
        }

        /**
         * 统一重试入口：把本次调用上下文封装为 RetryContext 再交给策略执行。
         *
         * <p>这里的设计思想是：
         * <ul>
         *     <li>业务调用方只关心“调用成功还是失败”；</li>
         *     <li>具体是同机重试、换节点重试，还是并发竞速，由策略对象决定；</li>
         *     <li>方法总超时必须被严格控制，不能因为重试把一个调用拖得无限久。</li>
         * </ul>
         */
        private Response doRetry(RpcCallMetrics rpcCallMetrics, List<ServiceMetadata> serviceMetadata) throws Exception {
            Throwable e = rpcCallMetrics.getThrowable();
            if (e instanceof ExecutionException ee && ee.getCause() instanceof RpcException rpcException && !rpcException.retry()) {
                throw rpcException;
            }
            Response response;
            long methodTime = consumerProperties.getMethodTimeOutMs() - rpcCallMetrics.getDuration();
            if (methodTime <= 0) {
                throw new TimeoutException("方法超时");
            }
            log.warn("rpc出现异常进行重试", e);
            RetryContext retryContext = createRetryContextFromFailMetrics(rpcCallMetrics, serviceMetadata, methodTime);
                     /*
    // 这是一个匿名内部类写法，等同于上面的 Lambda
retryContext.setDorpcFunction(new Function<ServiceMetadata, CompletableFuture<Response>>() {
    @Override
    public CompletableFuture<Response> apply(ServiceMetadata provider) {
        // 1. 构建请求对象 (使用外部传入的 method 和 args)
        RpcRequest request = buildRequest(method, args);

        // 2. 发起真正的异步 RPC 调用
        // 注意：这里使用了传入的 provider 作为目标
        return callRpcAsync(request, provider);
    }
});
     */
            // 这里真正执行策略重试；失败了重试，重试后还失败就继续把异常往上抛。
            response = this.retryPolicy.retry(retryContext);
            return response;
        }

        private @NonNull RetryContext createRetryContextFromFailMetrics(RpcCallMetrics rpcCallMetrics, List<ServiceMetadata> serviceMetadata, long methodTime) {
            RetryContext retryContext = new RetryContext();
            retryContext.setFailService(rpcCallMetrics.getServiceMetadata());
            retryContext.setServiceMetadataList(serviceMetadata);
            retryContext.setMethodTimeoutMs(methodTime);
            retryContext.setLoadBalancer(this.loadBalancer);
            retryContext.setRequestTimeoutMs(consumerProperties.getRequestTimeoutMs());
            retryContext.setDorpcFunction(provider -> {
                CircuitBreaker breaker = circuitBreakerManager.createOrGetBreaker(provider);
                if (!breaker.allowRequest()) {
                    //失败
                    CompletableFuture<Response> breakFuture = new CompletableFuture<>();
                    breakFuture.completeExceptionally(new RpcException("provider熔断了"));
                    return breakFuture;
                }
                RpcCallMetrics retryMetrics = RpcCallMetrics.createRpcCallMetrics(rpcCallMetrics.getMethod(), rpcCallMetrics.getParams(), provider);
                CompletableFuture<Response> requestFuture = callRpcAsync(buildRequest(rpcCallMetrics.getMethod(), rpcCallMetrics.getParams()), provider);
                requestFuture.whenComplete((r, RetryE) -> {
                    if (RetryE == null) {
                        retryMetrics.complete(r);
                    } else {
                        retryMetrics.errorComplete(RetryE);
                    }
                    breaker.recordRpc(retryMetrics);
                });
                return requestFuture;
            });
            return retryContext;
        }

        /**
         * 发起一次异步 RPC 调用。
         *
         * <p>这里做三件事：
         * <ol>
         *     <li>在 in-flight 表中登记 requestId -> Future，方便响应回来时做配对；</li>
         *     <li>获取或建立到目标 Provider 的连接；</li>
         *     <li>把 Request 写到 Netty Channel 中，失败时立即让 Future 进入异常态。</li>
         * </ol>
         */
        private CompletableFuture<Response> callRpcAsync(Request request, ServiceMetadata provider) {
            CompletableFuture<Response> responseFuture = inFlightRequestManager.inFlightRequestTable(request,
                    consumerProperties.getRequestTimeoutMs(),
                    provider);
            Channel channel = manager.getChannel(provider);
            if (channel == null) {
                responseFuture.completeExceptionally(new RpcException("Provider连接失败"));
                return responseFuture;
            }
            channel.writeAndFlush(request).addListener(f -> {
                log.info("发送请求{}到{}:{} questId{}", request, provider.getHost(), provider.getPort(), request.getRequestId());
                if (!f.isSuccess()) {
                    // 如果发送失败，说明这个请求根本没有真正到达 Provider，
                    // 这里直接把 Future 标记为异常，唤醒等待中的调用线程。
                    responseFuture.completeExceptionally(new RpcException("请求发送失败" + f.cause()));
                }
            });

            return responseFuture;
        }

        /**
         * 统一响应处理：成功返回业务值，失败抛出业务异常。
         *
         * <p>对调用方来说，远程响应最终要么是“业务结果”，要么是“业务异常”。
         * 这里做的是把协议层的 Response 转成方法调用层能理解的返回值。
         */
        private Object processResponse(Response response) {
            if (response.getCode() == 200) {
                return response.getRes();
            }
            throw new RpcException(response.getErrorMessage());
        }

        /**
         * 根据本次方法调用构建 RPC 请求体。
         *
         * <p>这一步相当于把“本地方法调用信息”翻译成“网络传输所需的协议对象”。
         * Provider 端收到这个对象后，就能根据 serviceName + methodName + paramsClass 进行反射调用。
         */
        private @NonNull Request buildRequest(Method method, Object[] args) {
            boolean genericInvoke = method.getName().equals("$invoke");
            // 组装本次 RPC 请求。
            Request request = new Request();
            // request.setMethodName("privateAdd"); // 仅用于测试不存在方法的异常路径。
            request.setGenericInvoke(genericInvoke);

            if (genericInvoke) {
                request.setParamsClassStr((String[]) args[2]);
                request.setServiceName(args[0].toString());
                request.setMethodName(args[1].toString());
                request.setParams((Object[])args[3]);
            } else {
                request.setParamsClass(method.getParameterTypes());
                request.setServiceName(interfaceClass.getName());
                request.setMethodName(method.getName());
                request.setParams(args);
            }

            return request;
        }
    }

}
