package tech.insight.kilsme.rpc.consumser;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.nullness.qual.NonNull;
import tech.insight.kilsme.rpc.codec.KilsmeDecoder;
import tech.insight.kilsme.rpc.codec.RequestEncoder;
import tech.insight.kilsme.rpc.exception.RpcException;
import tech.insight.kilsme.rpc.loadbalance.LoadBalancer;
import tech.insight.kilsme.rpc.loadbalance.RandomLoadBalancer;
import tech.insight.kilsme.rpc.loadbalance.RoundRobinLoadBalancer;
import tech.insight.kilsme.rpc.message.Request;
import tech.insight.kilsme.rpc.message.Response;
import tech.insight.kilsme.rpc.register.DefaultServiceRegister;
import tech.insight.kilsme.rpc.register.RegistryConfig;
import tech.insight.kilsme.rpc.register.ServiceMetadata;
import tech.insight.kilsme.rpc.register.ServiceRegistry;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Consumer 动态代理工厂：把本地接口调用转换为远程 RPC 调用。
 */
@Slf4j
public class ConsumerProxyFactory {
    //在途请求表：requestId -> 异步响应 Future。
    private final Map<Integer, CompletableFuture<Response>> inFlightRequestTable;
    // 连接管理器：复用到同一 Provider 地址的连接。
    private final ConnectionManager manager;
    private final ServiceRegistry registry;
    private final ConsumerProperties consumerProperties;

    public ConsumerProxyFactory(ConsumerProperties consumerProperties) throws Exception {
        // 使用统一门面，屏蔽具体注册中心实现差异。
        this.registry = new DefaultServiceRegister();
        this.registry.init(consumerProperties.getRegistryConfig());
        Bootstrap bootstrap = crateBootstrap(consumerProperties);
        this.manager = new ConnectionManager(bootstrap);
        this.inFlightRequestTable = new ConcurrentHashMap<>();
        this.consumerProperties = consumerProperties;
    }

    @SuppressWarnings("unchecked")
    // 为目标接口创建 JDK 动态代理。
    public <I> I createConsumerProxy(Class<I> interfaceClass) {
        //通过 JDK 动态代理把本地接口调用转为远程 RPC 请求。
        return (I) Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
                new Class[]{interfaceClass}, new ConsumerInvocationHandler(interfaceClass,createLoadBalancer()));
    }

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

    public class ConsumerInvocationHandler implements InvocationHandler {
        final Class<?> interfaceClass;
        final LoadBalancer loadBalancer;

        public ConsumerInvocationHandler(Class<?> interfaceClass, LoadBalancer loadBalancer) {
            this.interfaceClass = interfaceClass;
            this.loadBalancer = loadBalancer;
        }

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
            //consumer-->center  provider
            //注册中心 保存数据
            //通知机制
            //数据一致性
            //心跳维护，临时数据
            //nacos  zookeeper
            try {
                // 用 Future 承接异步响应，最后在方法尾部 get() 同步返回。
                CompletableFuture<Response> responseCompletableFuture = new CompletableFuture<>();
                // 1) 从注册中心查可用 Provider 列表。
                List<ServiceMetadata> serviceMetadata = registry.fetchServiceList(interfaceClass.getName());
                if (serviceMetadata.isEmpty()) {
                    throw new RpcException(interfaceClass.getName() + "没有找到服务");
                }
                // 2) 选择一个 Provider 并复用/创建连接。
                //使用负载均衡策略
                ServiceMetadata providerMetadata = loadBalancer.select(serviceMetadata);
                Channel channel = manager.getChannel(providerMetadata.getHost(), providerMetadata.getPort());
                if (channel == null) {
                    throw new RpcException("连接失败");
                }
                Request request = buildRequest(method, args);
                inFlightRequestTable.put(request.getRequestId(), responseCompletableFuture);//防止通信速度过快，导致response回来找不到request在table中
                channel.writeAndFlush(request).addListener(f -> {
                    if (!f.isSuccess()) {
                        // 发送成功后登记请求，等待 Provider 返回同 requestId 的响应。
                        inFlightRequestTable.remove(request.getRequestId());
                        responseCompletableFuture.completeExceptionally(new RpcException("请求发送失败" + f.cause()));
                    }
                });
                // 同步等待异步结果返回。 这个是阻塞等待
                Response response = responseCompletableFuture.get(consumerProperties.getRequestTimeoutMs(), TimeUnit.MILLISECONDS);
                return processResponse(response);
            } catch (RpcException rpcException) {
                throw rpcException;
            } catch (Exception e) {
                throw new RuntimeException("RPC 调用异常");
            }

        }

        private Object processResponse(Response response) {
            if (response.getCode() == 200) {
                return response.getRes();
            }
            throw new RpcException(response.getErrorMessage());
        }

        private @NonNull Request buildRequest(Method method, Object[] args) {
            // 组装本次 RPC 请求。
            Request request = new Request();
            request.setMethodName(method.getName());
            // request.setMethodName("privateAdd"); // 仅用于测试不存在方法的异常路径。
            request.setParams(args);
            request.setParamsClass(method.getParameterTypes());
            request.setServiceName(interfaceClass.getName());
            return request;
        }
    }

    // 创建客户端 Bootstrap，并配置编解码与响应处理器。
    private Bootstrap crateBootstrap(ConsumerProperties consumerProperties) {
        // Bootstrap 对应“客户端连接配置”。
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(new NioEventLoopGroup(consumerProperties.getWorkThreadNum()))
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, consumerProperties.getConnectTimeoutMs())
                // 客户端只有一个连接，因此使用 handler 初始化该连接的 pipeline。
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                        // 入站：先按协议解码，再交给业务 handler 处理 Response。
                        // 出站：RequestEncoder 在 writeAndFlush(Request) 时自动生效。
                        nioSocketChannel.pipeline()
                                .addLast(new KilsmeDecoder())
                                .addLast(new RequestEncoder())
                                // 业务入站处理器：收到响应后完成 Future，并关闭连接。
                                .addLast(new ConsumerHandler());
                    }
                });
        return bootstrap;
    }

    private class ConsumerHandler extends SimpleChannelInboundHandler<Response> {
        @Override
        protected void channelRead0(ChannelHandlerContext channelHandlerContext, Response response) throws Exception {
            //返回了相应的response请求
            CompletableFuture<Response> responseFuture = inFlightRequestTable.remove(response.getRequestId());
            if (responseFuture == null) {
                log.warn("未找到对应的请求，requestId={}", response.getRequestId());
                return;
            }
            responseFuture.complete(response);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            log.info("地址：{}连接了", ctx.channel().remoteAddress());
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            //链路发生异常
            log.error("发生了异常", cause);
            ctx.channel().close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            log.info("地址：{}断开了", ctx.channel().remoteAddress());
        }

    }

}
