package org.spring.study.netty.bbnb.project.niohdl.core;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;

import org.spring.study.netty.bbnb.project.niohdl.box.StringReceivePacket;
import org.spring.study.netty.bbnb.project.niohdl.box.StringSendPacket;
import org.spring.study.netty.bbnb.project.niohdl.impl.SocketChannelAdapter;
import org.spring.study.netty.bbnb.project.niohdl.impl.async.AsyncReceiveDispatcher;
import org.spring.study.netty.bbnb.project.niohdl.impl.async.AsyncSendDispatcher;

public class Connector implements Closeable, SocketChannelAdapter.OnChannelStatusChangedListener {

    private SocketChannel channel;
    private Sender sender;//这两个都引用适配器
    private Receiver receiver;
    private SendDispatcher sendDispatcher;
    private ReceiveDispatcher receiveDispatcher;

    protected void setup(SocketChannel channel) throws IOException {

        this.channel = channel;
        SocketChannelAdapter adapter = new SocketChannelAdapter(channel, ioContext.getIoSelector(), this);
        sender = adapter;
        receiver = adapter;

        sendDispatcher = new AsyncSendDispatcher(sender);
        receiveDispatcher = new AsyncReceiveDispatcher(receiver,receivePacketCallback);

        receiveDispatcher.start();

    }

    public void send(String msg){
        //System.out.println("发送:"+msg);
        SendPacket packet = new StringSendPacket(msg);
        sendDispatcher.send(packet);
    }


    protected void onReceiveFromCore(String msg) {
        System.out.println(msg);
    }



    //实现Closeable方法
    @Override
    public void close() throws IOException {
        sendDispatcher.close();
        receiveDispatcher.close();
        sender.close();
        receiver.close();
        channel.close();

    }

    //实现SocketChannelAdapter.OnChannelStatusChangedListener中的方法
    @Override
    public void onChannelClosed(SocketChannel channel) {

    }

    //接收AsyncReceiveDispatcher中的回调
    private ReceiveDispatcher.ReceivePacketCallback receivePacketCallback = new ReceiveDispatcher.ReceivePacketCallback() {
        //接收到消息的回调
        @Override
        public void onReceivePacketCompleted(ReceivePacket packet) {
            if(packet instanceof StringReceivePacket){
                String msg = packet.toString();
                onReceiveFromCore(msg);
            }
        }
    };
}
