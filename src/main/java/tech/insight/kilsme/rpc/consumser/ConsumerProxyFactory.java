package tech.insight.kilsme.rpc.consumser;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import tech.insight.kilsme.rpc.api.Add;
import tech.insight.kilsme.rpc.codec.KilsmeDecoder;
import tech.insight.kilsme.rpc.codec.RequestEncoder;
import tech.insight.kilsme.rpc.exception.RpcException;
import tech.insight.kilsme.rpc.message.Request;
import tech.insight.kilsme.rpc.message.Response;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ConsumerProxyFactory {
    //在途请求，没有拿到response的request
    private  final Map<Integer, CompletableFuture<Response>> inFlightRequestTable = new ConcurrentHashMap<>();
    //拿到连接管理器
    private  final ConnectionManager manager = new ConnectionManager(crateBootstrap());

    private Bootstrap crateBootstrap() {
        // Bootstrap 对应“客户端连接配置”。
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(new NioEventLoopGroup(4))
                .channel(NioSocketChannel.class)
                // 客户端只有一个连接，因此使用 handler 初始化该连接的 pipeline。
                .handler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) throws Exception {
                        // 入站：先按协议解码，再交给业务 handler 处理 Response。
                        // 出站：RequestEncoder 在 writeAndFlush(Request) 时自动生效。
                        nioSocketChannel.pipeline().addLast(new KilsmeDecoder())
                                .addLast(new RequestEncoder())
                                // 业务入站处理器：收到响应后完成 Future，并关闭连接。
                                .addLast(new SimpleChannelInboundHandler<Response>() {
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
                                });
                    }
                });
        return bootstrap;
    }

    public <I> I createConsumerProxy(Class<I> inserfaceClass) {
      return(I)Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[]{Add.class}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getDeclaringClass() == Object.class) {
                    if (method.getName().equals("toString")) {
                        return "YY Proxy Consumer"+inserfaceClass.getName();
                    }
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) {
                        return proxy == args[0];
                    }
                    throw new UnsupportedOperationException("代理对象不支持这个函数");
                }
                try {
                    // 用 Future 承接异步响应，最后在方法尾部 get() 同步返回。
                    CompletableFuture<Response> responseCompletableFuture = new CompletableFuture<>();
                    Channel channel = manager.getChannel("localhost", 8888);
                    if (channel == null) {
                        throw new RpcException("连接失败");
                    }
                    // 组装本次 RPC 请求。
                    Request request = new Request();
                    request.setMethodName(method.getName());
                    // request.setMethodName("privateAdd"); // 仅用于测试不存在方法的异常路径。
                    request.setParams(args);
                    request.setParamsClass(method.getParameterTypes());
                    request.setServiceName(inserfaceClass.getName());
                    channel.writeAndFlush(request).addListener(f -> {
                        if (f.isSuccess()) {
                            inFlightRequestTable.put(request.getRequestId(), responseCompletableFuture);
                        }
                    });
                    // 同步等待异步结果返回。 这个是阻塞等待
                    Response response = responseCompletableFuture.get(3, TimeUnit.SECONDS);
                    if (response.getCode() == 200) {
                        return response.getRes();
                    }
                    throw new RpcException(response.getErrorMessage());

                } catch (RpcException rpcException) {
                    throw rpcException;
                } catch (Exception e) {
                    throw new RuntimeException("RPC 调用异常");
                }
            }
        });
    }
}
