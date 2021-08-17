package org.spring.study.netty.protobuf;


import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

/**
 * Created by wangdecheng on 10/06/2018.
 */
public class SubReqServerHandler extends ChannelHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx,Object msg){
        SubscribeReqProto.SubscribeReq req = (SubscribeReqProto.SubscribeReq)msg;
        if("wangdecheng".equalsIgnoreCase(req.getUserName())){
            System.out.println("server accept client subscribe req:[" + req.toString() + "]");
            SubscribeRespProto.SubscribeResp subscribeResp = resp(req.getSubReqID());
            ctx.writeAndFlush(subscribeResp);
            System.out.println(" sent to client:"+subscribeResp);
        }
    }

    private SubscribeRespProto.SubscribeResp resp(int subReqID) {
        SubscribeRespProto.SubscribeResp.Builder builder = SubscribeRespProto.SubscribeResp.newBuilder();
        builder.setSubReqID(subReqID);
        builder.setRespCode(0);
        builder.setDesc("Netty book order successd!");
        return builder.build();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx,Throwable cause){
        cause.printStackTrace();
        ctx.close();
    }
}
