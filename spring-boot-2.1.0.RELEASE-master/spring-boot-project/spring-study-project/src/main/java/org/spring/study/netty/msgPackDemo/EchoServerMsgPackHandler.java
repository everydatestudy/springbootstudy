package org.spring.study.netty.msgPackDemo;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;

/**
 * Created by wangdecheng on 09/06/2018.
 */
public class EchoServerMsgPackHandler extends ChannelHandlerAdapter {

    int counter = 0;

    @Override
    public void channelRead(ChannelHandlerContext ctx,Object msg) throws Exception{
        try {
            //直接输出msg
            System.out.println(msg.toString());
            String remsg = new String("has receive");
            //回复has receive 给客户端
            ctx.write(msg);
            System.out.println("send reply to client");
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
        }
    }
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // TODO Auto-generated method stub
        ctx.flush();
    }
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause){
        cause.printStackTrace();
        ctx.close();
    }

}
