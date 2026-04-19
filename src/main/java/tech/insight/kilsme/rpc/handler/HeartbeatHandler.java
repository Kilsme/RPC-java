package tech.insight.kilsme.rpc.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import tech.insight.kilsme.rpc.message.HeartbeatRequest;
import tech.insight.kilsme.rpc.message.HeartbeatResponse;

//心跳检测的handler
public class HeartbeatHandler extends SimpleChannelInboundHandler<Object> {
    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object msg) throws Exception {
            // 收到心跳请求，什么都不做，保持连接活跃。
            // 这里可以添加日志记录或其他监控逻辑。
       if(msg instanceof  HeartbeatRequest request){
           channelHandlerContext.writeAndFlush(new HeartbeatResponse(request.getRequestTime()));
           return ;
       }
       if(msg instanceof  HeartbeatResponse response){
           long duration=System.currentTimeMillis()- response.getRequestTime();
           System.out.println( "接收到了心跳响应"+duration+"毫秒");
       }
       channelHandlerContext.fireChannelRead(msg);
    }
    /*
    userEventTriggered 方法正是用来处理 IdleStateHandler 发出的空闲信号的。
你可以把它想象成一个“事件监听器”。当 IdleStateHandler 监控到连接空闲时，它不会直接采取行动，
而是抛出一个事件。
Netty 框架会自动调用这个方法，把事件传递进来，让你决定该怎么处理。
     */

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
         if(evt instanceof IdleStateEvent idleStateEvent){
             IdleState state = idleStateEvent.state();
             if(state==IdleState.READER_IDLE){
                 //长时间没有收到消息，包括心跳检测，说明可能挂掉了，直接关闭
                   ctx.channel().close();
             }else if(state==IdleState.WRITER_IDLE){
                 //长时间没有发送消息，说明可能连接不稳定，发送一个心跳请求试试
                 ctx.writeAndFlush(new HeartbeatRequest());
             }
         }
         ctx.fireUserEventTriggered(evt);
    }
}
