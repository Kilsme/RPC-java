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

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
         if(evt instanceof IdleStateEvent idleStateEvent){
             IdleState state = idleStateEvent.state();
             if(state==IdleState.READER_IDLE){
                   ctx.channel().close();
             }else if(state==IdleState.WRITER_IDLE){
                 ctx.writeAndFlush(new HeartbeatRequest());
             }
         }
         ctx.fireUserEventTriggered(evt);
    }
}
